/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Checks whether the running Retromod build is the unmodified official one.
 *
 * <h2>How it works</h2>
 * The build embeds a SHA-256 of Retromod's own compiled classes
 * ({@link #EXPECTED_SELF_HASH}). At startup this class re-hashes its own
 * bytecode and compares: a match means "this is the official, unmodified
 * build"; a mismatch fires a fork notice in the log.
 *
 * <h2>Important: this is an integrity check, not cryptographic anti-tamper</h2>
 * There is <b>no secret key</b>. A determined attacker who edits the bytecode
 * can simply recompute the embedded hash (or strip this class). So this does
 * <i>not</i> stop a deliberate impersonator. What it reliably catches is
 * <b>accidental corruption</b> (a truncated/garbled download) and
 * <b>casual modification</b> (a repack that didn't bother to update the hash) —
 * for those, a build that <i>quietly</i> differs from official stands out.
 *
 * <p>For real verification, compare the JAR's SHA-256 against the value
 * <b>published on the official releases page</b> (Modrinth / GitHub show one for
 * every file). That reference lives out-of-band, where a tamperer can't change
 * it — which is the part an in-jar hash can't provide.
 *
 * <p>Verification never blocks: an unmodified, modified, or forked build all run
 * identically. The MIT license guarantees that. This is purely informational.
 *
 * <p>(Class name kept as {@code SignatureVerifier} for compatibility; it no
 * longer uses JAR signatures.)
 */
public final class SignatureVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /**
     * SHA-256 (uppercase hex) of Retromod's own classes — every
     * {@code com/retromod/} {@code .class} entry <i>except</i> this verifier
     * class (which carries the value, so it can't hash itself) and the
     * relocated {@code com/retromod/shaded/} dependencies. Those deps are
     * excluded because {@code build-all.sh} strips/varies them per loader, so
     * hashing only Retromod's own code keeps one value valid across every
     * shipped dist jar (the standard full builds; a {@code lite} build differs).
     *
     * <p>Empty in dev/source builds: the status is then {@link Status#UNKNOWN}
     * and the freshly computed hash is logged so a release build can embed it.
     * To cut a release: build, read the logged "Computed self-hash" line (or run
     * {@link #computeSelfHash(JarFile)} over the jar), paste it here, rebuild —
     * this class is excluded from the hash, so re-embedding doesn't invalidate
     * it. See {@code docs/authenticity.md}.
     */
    private static final String EXPECTED_SELF_HASH = "1D31369B465A7A1A429CEAA59A96F06B79A7E374FD92A6ECBE883ED64D63E024";

    /** This class's own jar entry — excluded from the hash (it carries the hash). */
    private static final String SELF_ENTRY = "com/retromod/security/SignatureVerifier.class";

    private static final String EXPECTED_IMPL_TITLE = "Retromod";

    private static volatile VerificationResult cachedResult;

    private SignatureVerifier() {}

    // ──────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ──────────────────────────────────────────────────────────────────────

    /** Verify the running Retromod build and log the result. Called once at init. */
    public static VerificationResult verifyAndLog() {
        VerificationResult result = verify();
        logResult(result);
        return result;
    }

    /** Verify the running build. Cached after the first call. */
    public static VerificationResult verify() {
        VerificationResult cached = cachedResult;
        if (cached != null) return cached;
        synchronized (SignatureVerifier.class) {
            if (cachedResult != null) return cachedResult;
            cachedResult = doVerify();
            return cachedResult;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // VERIFICATION LOGIC
    // ──────────────────────────────────────────────────────────────────────

    private static VerificationResult doVerify() {
        Path jarPath = findOwnJar();
        if (jarPath == null || !Files.exists(jarPath)) {
            return new VerificationResult(Status.UNKNOWN,
                    "Not running from a JAR (dev/source build)", null, null);
        }

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Sanity check: the manifest identifies this as Retromod.
            Manifest manifest = jar.getManifest();
            String implTitle = (manifest != null)
                    ? manifest.getMainAttributes().getValue("Implementation-Title")
                    : null;
            if (implTitle != null && !EXPECTED_IMPL_TITLE.equalsIgnoreCase(implTitle)) {
                return new VerificationResult(Status.IMPOSTOR,
                        "JAR claims Implementation-Title=" + implTitle + " (not Retromod)",
                        jarPath, null);
            }

            String actual = computeSelfHash(jar);
            if (actual == null) {
                return new VerificationResult(Status.UNKNOWN, "No class entries to hash",
                        jarPath, null);
            }

            String expected = EXPECTED_SELF_HASH.trim();
            if (expected.isEmpty()) {
                // Dev/source build: no embedded reference. Surface the computed
                // value (logResult prints it) so a release build can embed it.
                return new VerificationResult(Status.UNKNOWN,
                        "Self-hash not embedded in this build", jarPath, actual);
            }
            if (actual.equalsIgnoreCase(expected)) {
                return new VerificationResult(Status.OFFICIAL,
                        "Bytecode matches the official build hash", jarPath, actual);
            }
            return new VerificationResult(Status.MODIFIED,
                    "Bytecode differs from the official build hash", jarPath, actual);

        } catch (Exception e) {
            return new VerificationResult(Status.UNKNOWN,
                    "Could not verify: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    jarPath, null);
        }
    }

    /**
     * SHA-256 over Retromod's own {@code com/retromod/} classes — sorted by
     * name, excluding this class ({@link #SELF_ENTRY}) and the relocated
     * {@code com/retromod/shaded/} dependencies — hashing each entry's name
     * (UTF-8) then its bytes. Uppercase hex, or {@code null} if no class entries.
     *
     * <p>Package-private so the release tooling/tests can compute the same value.
     */
    static String computeSelfHash(JarFile jar) throws Exception {
        List<JarEntry> classes = new ArrayList<>();
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if (je.isDirectory()) continue;
            String n = je.getName();
            if (!n.endsWith(".class")) continue;
            // Only Retromod's OWN classes — these are identical across every
            // shipped variant. Exclude the relocated dependencies under
            // com/retromod/shaded/ (build-all.sh strips/varies them per loader),
            // and this verifier class (it carries the expected hash).
            if (!n.startsWith("com/retromod/")) continue;
            if (n.startsWith("com/retromod/shaded/")) continue;
            if (n.equals(SELF_ENTRY)) continue;
            classes.add(je);
        }
        if (classes.isEmpty()) return null;
        classes.sort(Comparator.comparing(JarEntry::getName));

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        for (JarEntry je : classes) {
            md.update(je.getName().getBytes(StandardCharsets.UTF_8));
            try (InputStream is = jar.getInputStream(je)) {
                int r;
                while ((r = is.read(buf)) != -1) md.update(buf, 0, r);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /** Locate the JAR this class is loaded from, or null if running from a directory. */
    private static Path findOwnJar() {
        try {
            var codeSource = SignatureVerifier.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) return null;
            var url = codeSource.getLocation();
            if (url == null) return null;

            String path = url.getPath();
            int bang = path.indexOf('!');
            if (bang >= 0) path = path.substring(0, bang);
            if (path.startsWith("file:")) path = path.substring("file:".length());

            Path p = Path.of(path);
            if (Files.isDirectory(p)) return null; // running from classes dir, not a JAR
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // FORK NOTICE
    // ──────────────────────────────────────────────────────────────────────
    //
    // Plain string template — by design. Anyone forking Retromod can change
    // this text trivially (it's MIT-licensed; that's their right). The deterrent
    // is social: if honest forks keep it, a build that's silently missing it is
    // the red flag. Clarity over cleverness.

    private static final String FORK_NOTICE_TEMPLATE =
        "You are using a %s Fork. If this was advertised as the official %s, "
        + "this is NOT official! Check github.com/Bownlux/%s for the real thing.";

    private static String forkNotice() {
        return String.format(FORK_NOTICE_TEMPLATE,
                EXPECTED_IMPL_TITLE, EXPECTED_IMPL_TITLE, EXPECTED_IMPL_TITLE);
    }

    /**
     * @deprecated the notice is now emitted automatically by {@link #logResult}
     *     whenever the status is not OFFICIAL. Kept as a no-op shim for any
     *     external callers.
     */
    @Deprecated
    public static void logForkNotice() {
        LOGGER.warn("[Retromod] {}", forkNotice());
    }

    private static void logResult(VerificationResult result) {
        switch (result.status()) {
            case OFFICIAL -> LOGGER.info("[Retromod] ✓ Authenticity: OFFICIAL build — {}",
                    result.detail());
            case MODIFIED -> {
                LOGGER.warn("[Retromod] ⚠ Authenticity: MODIFIED build — {}",
                        result.detail());
                LOGGER.warn("[Retromod] {}", forkNotice());
            }
            case IMPOSTOR -> {
                LOGGER.error("[Retromod] ✗ Authenticity: IMPOSTOR — {}",
                        result.detail());
                LOGGER.error("[Retromod] {}", forkNotice());
            }
            case UNKNOWN -> {
                LOGGER.debug("[Retromod] Authenticity: unknown — {}", result.detail());
                // Dev/source build with no embedded hash: surface the computed value
                // at INFO so a release build can embed it in EXPECTED_SELF_HASH.
                if (result.selfHash() != null
                        && "Self-hash not embedded in this build".equals(result.detail())) {
                    LOGGER.info("[Retromod] Computed self-hash (embed in EXPECTED_SELF_HASH "
                            + "for release): {}", result.selfHash());
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // RESULT TYPES
    // ──────────────────────────────────────────────────────────────────────

    public enum Status {
        /** Bytecode matches the embedded official hash — unmodified official build. */
        OFFICIAL,
        /** Bytecode differs from the official hash — a fork, repack, or corruption. */
        MODIFIED,
        /** Manifest says this JAR isn't Retromod at all. */
        IMPOSTOR,
        /** Could not determine — dev/source build, no embedded hash, or unreadable. */
        UNKNOWN,
    }

    public record VerificationResult(Status status, String detail,
                                     Path jarPath, String selfHash) {

        /** Is this the unmodified official build? */
        public boolean isOfficial() { return status == Status.OFFICIAL; }

        /** Should the user be nudged that this might not be the genuine build? */
        public boolean isSuspicious() {
            return status == Status.MODIFIED || status == Status.IMPOSTOR;
        }

        public String displayLine() {
            return switch (status) {
                case OFFICIAL -> "§aOfficial Retromod build§r";
                case MODIFIED -> "§eModified / unofficial build§r";
                case IMPOSTOR -> "§cNot Retromod (manifest mismatch)§r";
                case UNKNOWN  -> "§7Authenticity unknown§r";
            };
        }
    }
}

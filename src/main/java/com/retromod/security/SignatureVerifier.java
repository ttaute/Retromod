/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.*;

/**
 * Verifies the authenticity of the running Retromod JAR.
 *
 * <h2>Purpose</h2>
 * Retromod is MIT-licensed — anyone can fork, modify, and redistribute it.
 * But if a mod calls itself "Retromod" and asks users to trust it with mod
 * transformation (which involves reading and modifying JARs on disk), users
 * deserve a way to tell an official build from a malicious impersonator.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Official releases are signed with {@code jarsigner} using the
 *       Retromod release key. The public certificate is embedded as
 *       {@link #OFFICIAL_CERT_SHA256}.</li>
 *   <li>On startup, this class locates the JAR it is loaded from, reads
 *       the signing certificates, and compares fingerprints.</li>
 *   <li>Result is recorded in {@link VerificationResult} — logged and
 *       surfaced in the config screen so users can see it at a glance.</li>
 * </ol>
 *
 * <h2>Important: verification does not block</h2>
 * An unsigned, modified, or third-party build still runs normally. The MIT
 * license guarantees that right. The verifier is purely informational —
 * "you are running an official Retromod build" vs "this build is not signed
 * by Bownlux; it may be modified or unofficial."
 */
public final class SignatureVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /**
     * SHA-256 fingerprint of the Retromod release signing certificate.
     *
     * <p>Populated at release time. When unset (zeros), signature checking
     * is in "skeleton mode" — the code still runs but always reports
     * {@link Status#UNSIGNED} because there is no cert to compare against.
     * This is intentional: the infrastructure is in the codebase from day
     * one so release-candidate builds don't need to change code to become verifiable.
     *
     * <p>Format: 64 hex characters, uppercase, no separators. Example:
     * {@code "A1B2C3..."}.
     */
    private static final String OFFICIAL_CERT_SHA256 = "3580D51116872552FF43BF0660C1A67FE881D9F836524B8BE094612CC69652A3";

    /** Minimum parts of the manifest that must match for a "real" Retromod. */
    private static final String EXPECTED_IMPL_TITLE = "Retromod";

    private static volatile VerificationResult cachedResult;

    private SignatureVerifier() {}

    // ──────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Verify the running Retromod JAR and log the result.
     * Called once during mod initialization.
     */
    public static VerificationResult verifyAndLog() {
        VerificationResult result = verify();
        logResult(result);
        return result;
    }

    /**
     * Verify the running Retromod JAR. Result is cached — subsequent calls
     * return the same result without re-reading the JAR.
     */
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
            return new VerificationResult(Status.UNKNOWN, "Could not locate Retromod JAR",
                    null, null);
        }

        try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
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

            // Read and validate each class entry — JarFile verifies signatures
            // lazily as entries are read. We need to read through to completion
            // for the certs to be populated.
            List<Certificate> collectedCerts = new ArrayList<>();
            boolean anyClassEntries = false;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("META-INF/")) continue; // skip manifest/sigs
                if (!name.endsWith(".class")) continue;
                anyClassEntries = true;

                // Consume the entry — this triggers signature verification
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] buf = new byte[8192];
                    while (is.read(buf) != -1) { /* drain */ }
                }

                Certificate[] certs = entry.getCertificates();
                if (certs != null) {
                    for (Certificate c : certs) {
                        if (!collectedCerts.contains(c)) collectedCerts.add(c);
                    }
                }
            }

            if (!anyClassEntries) {
                return new VerificationResult(Status.UNKNOWN,
                        "JAR contains no class entries", jarPath, null);
            }

            if (collectedCerts.isEmpty()) {
                return new VerificationResult(Status.UNSIGNED,
                        "JAR is not signed", jarPath, null);
            }

            // Compare against the embedded official cert fingerprint
            String expected = OFFICIAL_CERT_SHA256 == null ? "" : OFFICIAL_CERT_SHA256.trim();
            if (expected.isEmpty()) {
                return new VerificationResult(Status.UNSIGNED,
                        "JAR is signed but no official cert fingerprint is embedded " +
                                "in this build (development/release-candidate build)",
                        jarPath, fingerprint(collectedCerts.get(0)));
            }

            for (Certificate cert : collectedCerts) {
                String fp = fingerprint(cert);
                if (fp != null && fp.equalsIgnoreCase(expected)) {
                    String subject = cert instanceof X509Certificate x
                            ? x.getSubjectX500Principal().getName()
                            : "(unknown)";
                    return new VerificationResult(Status.OFFICIAL,
                            "Signed by " + subject, jarPath, fp);
                }
            }

            return new VerificationResult(Status.UNOFFICIAL,
                    "JAR is signed but not by the official Retromod key",
                    jarPath, fingerprint(collectedCerts.get(0)));

        } catch (SecurityException se) {
            return new VerificationResult(Status.TAMPERED,
                    "Signature verification failed: " + se.getMessage(),
                    jarPath, null);
        } catch (Exception e) {
            return new VerificationResult(Status.UNKNOWN,
                    "Could not verify: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    jarPath, null);
        }
    }

    /**
     * Locate the JAR containing this class on disk.
     */
    private static Path findOwnJar() {
        try {
            var codeSource = SignatureVerifier.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) return null;
            var url = codeSource.getLocation();
            if (url == null) return null;

            String path = url.getPath();
            // Strip any trailing bang path (e.g. jar URLs)
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

    private static String fingerprint(Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // LOGGING
    // ──────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────────
    // FORK NOTICE
    // ──────────────────────────────────────────────────────────────────────
    //
    // Plain string template — by design. Anyone forking Retromod can
    // change this text trivially (it's MIT-licensed; that's their right).
    // The previous implementation XOR-encoded the bytes and decoded them
    // at runtime as a soft anti-tamper trick. That's exactly the pattern
    // automated malware scanners flag, and the deterrent value was low
    // (a fork can sed the string out either way). Clarity over cleverness.

    private static final String FORK_NOTICE_TEMPLATE =
        "You are using a %s Fork. If this was advertised as the official %s, "
        + "this is NOT official! Check github.com/Bownlux/%s for the real thing.";

    private static String forkNotice() {
        return String.format(FORK_NOTICE_TEMPLATE,
                EXPECTED_IMPL_TITLE, EXPECTED_IMPL_TITLE, EXPECTED_IMPL_TITLE);
    }

    /**
     * @deprecated the notice is now emitted automatically by {@link #logResult}
     *     whenever the status is not OFFICIAL. Kept as a no-op for backward
     *     compatibility with any external callers.
     */
    @Deprecated
    public static void logForkNotice() {
        LOGGER.warn("[Retromod] {}", forkNotice());
    }

    private static void logResult(VerificationResult result) {
        switch (result.status()) {
            case OFFICIAL -> LOGGER.info("[Retromod] \u2713 Authenticity: OFFICIAL build \u2014 {}",
                    result.detail());
            case UNSIGNED -> {
                LOGGER.info("[Retromod] Authenticity: unsigned build ({})", result.detail());
                LOGGER.warn("[Retromod] {}", forkNotice());
            }
            case UNOFFICIAL -> {
                LOGGER.warn("[Retromod] \u26a0 Authenticity: UNOFFICIAL build \u2014 {}",
                        result.detail());
                LOGGER.warn("[Retromod] {}", forkNotice());
            }
            case TAMPERED -> {
                LOGGER.warn("[Retromod] \u2717 Authenticity: TAMPERED \u2014 {}",
                        result.detail());
                LOGGER.warn("[Retromod] {}", forkNotice());
            }
            case IMPOSTOR -> {
                LOGGER.error("[Retromod] \u2717 Authenticity: IMPOSTOR \u2014 {}",
                        result.detail());
                LOGGER.error("[Retromod] {}", forkNotice());
            }
            case UNKNOWN -> LOGGER.debug("[Retromod] Authenticity: unknown \u2014 {}",
                    result.detail());
        }
        if (result.fingerprint() != null) {
            LOGGER.debug("[Retromod] Cert fingerprint: {}", result.fingerprint());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // RESULT TYPES
    // ──────────────────────────────────────────────────────────────────────

    public enum Status {
        /** Signed with the official Retromod key — safe to trust as authentic. */
        OFFICIAL,
        /** Not signed. Development/release-candidate build or a modified fork. Still safe if you trust the source. */
        UNSIGNED,
        /** Signed, but not by the official key. A fork or third-party build. */
        UNOFFICIAL,
        /** Signatures present but verification failed — JAR was modified after signing. */
        TAMPERED,
        /** Manifest says this JAR is not Retromod at all. */
        IMPOSTOR,
        /** Could not determine — e.g. running from a directory, not a JAR. */
        UNKNOWN,
    }

    public record VerificationResult(Status status, String detail,
                                      Path jarPath, String fingerprint) {

        /** Is this an authentic, officially signed build? */
        public boolean isOfficial() { return status == Status.OFFICIAL; }

        /** Should we warn the user that this might not be trustworthy? */
        public boolean isSuspicious() {
            return status == Status.TAMPERED
                || status == Status.IMPOSTOR
                || status == Status.UNOFFICIAL;
        }

        public String displayLine() {
            return switch (status) {
                case OFFICIAL   -> "\u00a7aOfficial Retromod build\u00a7r";
                case UNSIGNED   -> "\u00a77Unsigned build\u00a7r";
                case UNOFFICIAL -> "\u00a7eUnofficial build\u00a7r";
                case TAMPERED   -> "\u00a7cTampered \u2014 signature invalid\u00a7r";
                case IMPOSTOR   -> "\u00a7cNot Retromod (manifest mismatch)\u00a7r";
                case UNKNOWN    -> "\u00a77Authenticity unknown\u00a7r";
            };
        }
    }
}

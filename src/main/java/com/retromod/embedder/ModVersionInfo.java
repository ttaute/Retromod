/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.embedder;

import java.util.Set;

/**
 * Information about a mod's version and compatibility requirements.
 */
public record ModVersionInfo(
    String modId,
    String modVersion,
    String targetMcVersion,
    String modLoaderType,      // "fabric", "forge", "neoforge"
    String modLoaderVersion,
    Set<String> modPackages,   // Java packages containing mod code
    Set<String> apiDependencies,
    boolean usesRemovedApis
) {
    /**
     * Check if this mod needs transformation to work on the target version.
     *
     * <p>The comparison precision follows what the mod actually declared, which is
     * the key to getting both of these right at once:
     *
     * <ul>
     *   <li><b>A specific patch version</b> ({@code "1.21.1"}, three components):
     *       compared at full precision. A mod built for 1.21.1 on a 1.21.11 host
     *       genuinely needs translation (MC changed APIs across those patches), so
     *       {@code 1.21.1 < 1.21.11} → transform.</li>
     *   <li><b>A whole-minor floor</b> ({@code "1.20"}, two components): compared at
     *       minor precision. Forge mods declare their MC dependency as a range like
     *       {@code [1.20,)}, so the detector reports the floor {@code "1.20"}. On a
     *       1.20.1 host that's the same minor and the mod runs natively, so it's
     *       skipped. Comparing the floor at patch precision was the #84 bug:
     *       {@code "1.20" < "1.20.1"} wrongly looked like "needs transformation"
     *       and Retromod backed up and rewrote a mod that already worked.</li>
     * </ul>
     *
     * <p>So a 1.20.x mod on a 1.20.1 host is left alone (#84), while a 1.21.1 mod
     * on a 1.21.11 host is still translated. A {@code null} or unparseable target
     * is left alone rather than transformed on a guess.
     */
    public boolean needsTransformation(String currentMcVersion) {
        if (targetMcVersion == null || currentMcVersion == null
                || targetMcVersion.equals(currentMcVersion)) {
            return false;
        }

        int[] target = parseVersion(targetMcVersion);
        int[] current = parseVersion(currentMcVersion);
        if (target.length < 2 || current.length < 2) {
            return false; // unparseable version on either side — don't guess
        }

        // Compare at the precision the MOD declared. A bare major.minor target
        // (e.g. "1.20", a Forge range floor) is a whole-minor declaration → compare
        // minor-to-minor so same-minor patches don't trigger a needless transform.
        // A specific "major.minor.patch" target is compared in full so an older
        // patch on a newer-patch host (1.21.1 → 1.21.11) is correctly translated.
        if (target.length == 2) {
            int[] currentMinor = {current[0], current[1]};
            return compareVersions(target, currentMinor) < 0;
        }
        return compareVersions(target, current) < 0;
    }
    
    private int[] parseVersion(String version) {
        // Strip pre-release suffix (e.g., "1.0.0-beta.6" -> "1.0.0")
        int hyphen = version.indexOf('-');
        if (hyphen > 0) version = version.substring(0, hyphen);

        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
    
    private int compareVersions(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }
}

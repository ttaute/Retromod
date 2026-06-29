/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.bridge;

/**
 * One method in a mod class that is a candidate for bridge synthesis.
 *
 * <p>The mod declares an override of a parent method whose name was renamed in the
 * target MC version. Without a bridge the override is orphaned: the parent no longer
 * has the old name, so dispatch hits the parent's renamed version instead of the
 * mod's. The fix is to synthesize a method under the renamed {@code bridgeName} that
 * delegates to the mod's original, so virtual dispatch reaches the override again.
 * {@code access} is reused from the declared method.</p>
 *
 * <p>Only descriptor-preserving renames are modelled. A changed descriptor (added
 * parameter) would need argument conversion in the bridge body, left out of v1 to
 * limit VerifyError risk. See the scope notes in {@code design-3-4.md}.</p>
 */
public record BridgeCandidate(
        int access,
        String methodName,
        String methodDescriptor,
        String bridgeName
) {
}

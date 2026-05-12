/*
 * Compile-time stub for Forge's @Mod annotation.
 *
 * Retromod's main project uses the same trick (see
 * MC-Retromod/src/main/java/net/minecraftforge/fml/common/Mod.java) so the
 * Forge entry-point class can compile against this stub and be shadowed at
 * runtime by the real Forge annotation when the mod loads on a Forge
 * client. The runtime Forge classes win the classloader race because they
 * arrive earlier in Forge's mod-loading sequence.
 *
 * This file is identical in shape to the one in Retromod proper — the
 * stub only needs to declare the right interface, not implement anything.
 */
package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}

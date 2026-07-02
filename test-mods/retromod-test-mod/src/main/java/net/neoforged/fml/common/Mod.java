/*
 * Compile-time stub for NeoForge's @Mod annotation.
 *
 * Same trick as the Forge stub next door: this lets the NeoForge entry
 * point compile against a known annotation shape, and the real NeoForge
 * annotation shadows it at runtime when the mod loads on NeoForge.
 */
package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}

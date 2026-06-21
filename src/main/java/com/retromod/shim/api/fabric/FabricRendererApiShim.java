/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Fabric Renderer API relocation shim.
 *
 * <h2>What changed</h2>
 * Around Fabric API 0.110+ (for 1.21.x) the {@code fabric-renderer-api-v1} module
 * moved every public type from {@code net/fabricmc/fabric/api/renderer/v1/*} to
 * {@code net/fabricmc/fabric/api/client/renderer/v1/*} - inserting {@code /client/}
 * to match the rest of the client-side Fabric API packages. Same class names,
 * same APIs (for the surviving ones), just a new package prefix.
 *
 * <p>This breaks every pre-relocation mod that imports the old paths the moment
 * it's run on a current Fabric API. Confirmed-affected: Continuity, [EMF] Entity
 * Model Features, [ETF] Entity Texture Features, plus anything else built against
 * the indigo-driven custom mesh renderer pre-2024 (compat-audit run found 6 mods
 * in the top-30 hitting this).</p>
 *
 * <h2>What this shim covers</h2>
 * The surviving-but-renamed types under {@code mesh/} and {@code model/} -
 * straight 1:1 class redirects. The transformer rewrites every reference to the
 * old path in mod bytecode (descriptors, INVOKE owners, CONSTANT_Class entries)
 * to the new path during pre-launch.
 *
 * <h2>What this shim does NOT cover</h2>
 * The {@code material/} subtree ({@code RenderMaterial}, {@code BlendMode},
 * {@code MaterialFinder}) was REMOVED outright in the relocation rather than
 * moved - the material concept was rewritten. A mod that uses those types
 * (Continuity is the biggest one) needs a deeper API-bridging pass, not just
 * a rename. That's tracked as follow-up; this shim cuts the bytecode load-time
 * crashes down to just the material refs.
 */
public class FabricRendererApiShim implements VersionShim {

    private static final String OLD = "net/fabricmc/fabric/api/renderer/v1/";
    private static final String NEW = "net/fabricmc/fabric/api/client/renderer/v1/";

    /**
     * Survived-the-relocation classes - verified by listing the modern jar
     * (fabric-renderer-api-v1 13.0.0 ships exactly these at the new path).
     * If a future Fabric API release re-deletes one of these, the redirect
     * still fires but the call resolves to nothing and the mod gets a clean
     * NoSuchClassDefError pointing at the new name - easier to diagnose than
     * the symptom-on-load-of-an-old-name we have today.
     */
    private static final String[] MOVED_CLASSES = {
        // mesh/
        "mesh/Mesh", "mesh/MeshView", "mesh/MutableMesh",
        "mesh/MutableQuadView", "mesh/QuadAtlas", "mesh/QuadEmitter",
        "mesh/QuadTransform", "mesh/QuadView", "mesh/ShadeMode",
        // model/
        "model/ModelHelper",
        // top-level
        "Renderer",
    };

    @Override
    public String getShimName() {
        return "Fabric Renderer API Relocation";
    }

    @Override
    public String getSourceVersion() {
        // The old paths were stable across Fabric API ~0.50-0.109 (1.18-1.21.x).
        return "0.50.0";
    }

    @Override
    public String getTargetVersion() {
        // 0.110 was the first build with the /client/ relocation; 26.1 is on 0.145+.
        return "0.110.0";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        for (String cls : MOVED_CLASSES) {
            transformer.registerClassRedirect(OLD + cls, NEW + cls);
        }
    }
}

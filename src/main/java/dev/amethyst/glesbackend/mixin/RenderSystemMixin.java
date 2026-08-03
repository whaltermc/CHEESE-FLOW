package dev.amethyst.glesbackend.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amethyst.glesbackend.GLESBackendMod;
import dev.amethyst.glesbackend.gl.GLESCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stubs or replaces RenderSystem calls that use desktop-only GL features.
 *
 * Key targets:
 *  - polygonMode (GL_POLYGON_MODE does not exist in ES)
 *  - lineWidth (ES only guarantees width = 1.0)
 *  - depthRange (ES uses float variant)
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {

    /**
     * glPolygonMode is not available in OpenGL ES 3.x.
     * Minecraft calls this for debug wireframe rendering.
     * We no-op it on ES — wireframe would require a geometry shader workaround.
     */
    @Inject(
            method = "polygonMode",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onPolygonMode(int face, int mode, CallbackInfo ci) {
        if (GLESBackendMod.isGLESContext) {
            // Cancel the original call — would crash on ES since glPolygonMode doesn't exist
            ci.cancel();
        }
    }

    /**
     * Redirect lineWidth to our compat wrapper which clamps to 1.0 on ES.
     * Wide lines are an optional ES extension and not guaranteed on Android drivers.
     */
    @Inject(
            method = "lineWidth",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onLineWidth(float width, CallbackInfo ci) {
        if (GLESBackendMod.isGLESContext) {
            GLESCompat.lineWidth(width);
            ci.cancel();
        }
    }
}

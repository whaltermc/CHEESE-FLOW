package dev.amethyst.glesbackend.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amethyst.glesbackend.GLESBackendMod;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into GameRenderer initialization to:
 *  1. Verify the GL context is actually ES 3.2
 *  2. Log available ES extensions relevant to rendering quality
 *  3. Set any ES-specific global GL state
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void onInit(CallbackInfo ci) {
        if (!GLESBackendMod.isGLESContext) return;

        RenderSystem.assertOnRenderThread();

        String version = org.lwjgl.opengles.GLES20.glGetString(org.lwjgl.opengles.GLES20.GL_VERSION);
        String renderer = org.lwjgl.opengles.GLES20.glGetString(org.lwjgl.opengles.GLES20.GL_RENDERER);
        String vendor = org.lwjgl.opengles.GLES20.glGetString(org.lwjgl.opengles.GLES20.GL_VENDOR);

        GLESBackendMod.LOGGER.info("[GLES Backend] GL Version  : {}", version);
        GLESBackendMod.LOGGER.info("[GLES Backend] GL Renderer : {}", renderer);
        GLESBackendMod.LOGGER.info("[GLES Backend] GL Vendor   : {}", vendor);

        // Verify we actually have ES 3.2 — earlier versions lack features we depend on
        if (version != null && !version.contains("3.2") && !version.contains("3.1") && !version.contains("3.0")) {
            GLESBackendMod.LOGGER.error(
                    "[GLES Backend] WARNING: Context is not ES 3.x! Some features may be missing. Version: {}",
                    version
            );
        }

        // Log optional extensions that Minecraft could benefit from
        String extensions = org.lwjgl.opengles.GLES20.glGetString(org.lwjgl.opengles.GLES20.GL_EXTENSIONS);
        if (extensions != null) {
            boolean hasS3TC = extensions.contains("GL_EXT_texture_compression_s3tc")
                    || extensions.contains("GL_ANGLE_texture_compression_dxt");
            boolean hasETC2 = extensions.contains("GL_OES_compressed_ETC2"); // core in ES 3.0
            boolean hasAF   = extensions.contains("GL_EXT_texture_filter_anisotropic");

            GLESBackendMod.LOGGER.info("[GLES Backend] S3TC compression : {}", hasS3TC);
            GLESBackendMod.LOGGER.info("[GLES Backend] ETC2 compression : {}", hasETC2);
            GLESBackendMod.LOGGER.info("[GLES Backend] Anisotropic filter: {}", hasAF);
        }
    }
}

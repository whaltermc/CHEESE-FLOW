package dev.amethyst.glesbackend.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.amethyst.glesbackend.GLESBackendMod;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patches framebuffer attachment setup in RenderTarget for ES 3.2 compatibility.
 *
 * Key issues:
 *  1. GL_DEPTH_COMPONENT (unsized) → GL_DEPTH_COMPONENT24 (sized, required in ES)
 *  2. GL_DEPTH_STENCIL (unsized) → GL_DEPTH24_STENCIL8
 *  3. Renderbuffer internal formats follow the same sized requirement
 *
 * In 1.20, RenderTarget creates both color and depth attachments.
 * MainTarget (extends RenderTarget) also allocates a depth renderbuffer.
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

    /**
     * Redirects the renderbuffer storage call for depth attachments.
     * Desktop GL accepts GL_DEPTH_COMPONENT (unsized); ES does not.
     *
     * Targets the glRenderbufferStorage call inside createBuffers().
     */
    @Redirect(
            method = "createBuffers(IIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL30;glRenderbufferStorage(III)V"
            )
    )
    private void redirectRenderbufferStorage(int target, int internalFormat, int width, int height) {
        if (GLESBackendMod.isGLESContext) {
            // Map unsized depth format to sized equivalent required by ES
            int esFormat = switch (internalFormat) {
                case 0x1902 /* GL_DEPTH_COMPONENT */ -> GL30.GL_DEPTH_COMPONENT24;
                case 0x84F9 /* GL_DEPTH_STENCIL   */ -> GL30.GL_DEPTH24_STENCIL8;
                default -> internalFormat;
            };
            GL30.glRenderbufferStorage(target, esFormat, width, height);
        } else {
            GL30.glRenderbufferStorage(target, internalFormat, width, height);
        }
    }

    /**
     * After framebuffer creation, validate it and log any ES-specific errors.
     * Framebuffer incompleteness errors on ES often have different causes than desktop.
     */
    @Inject(
            method = "checkStatus",
            at = @At("HEAD")
    )
    private void beforeCheckStatus(CallbackInfo ci) {
        if (!GLESBackendMod.isGLESContext) return;

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GLESBackendMod.LOGGER.error(
                    "[GLES Backend] Framebuffer incomplete: 0x{} — likely a format issue on this ES driver",
                    Integer.toHexString(status)
            );
        }
    }
}

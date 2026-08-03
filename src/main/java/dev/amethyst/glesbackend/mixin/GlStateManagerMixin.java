package dev.amethyst.glesbackend.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.amethyst.glesbackend.GLESBackendMod;
import dev.amethyst.glesbackend.gl.GLESCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Patches GlStateManager to remap texture internal formats to ES 3.2 safe equivalents.
 *
 * ES 3.2 requires sized internal formats in glTexImage2D / glTexStorage2D.
 * Minecraft sometimes passes unsized formats (GL_RGB, GL_RGBA) which are
 * technically invalid in a strict ES 3.2 context.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {

    /**
     * Intercepts glTexImage2D calls and remaps the internalformat argument.
     *
     * In ES 3.2 strict mode, GL_RGB and GL_RGBA as internalformat are not valid.
     * We remap them to their sized equivalents (GL_RGB8, GL_RGBA8).
     */
    @ModifyArg(
            method = "_texImage2D",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"
            ),
            index = 2 // internalformat is the 3rd argument (index 2)
    )
    private static int patchTexImage2DFormat(int internalFormat) {
        if (!GLESBackendMod.isGLESContext) return internalFormat;
        return GLESCompat.mapInternalFormat(internalFormat);
    }

    /**
     * Intercepts glTexSubImage2D — format here is the pixel format, not internal.
     * ES 3.2 is strict about format/type combinations matching the internal format.
     *
     * For now we pass through since Minecraft's combinations are generally valid,
     * but this is a hook point for future fixes.
     */
    @ModifyArg(
            method = "_texSubImage2D",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glTexSubImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"
            ),
            index = 6 // format argument
    )
    private static int patchTexSubImage2DFormat(int format) {
        if (!GLESBackendMod.isGLESContext) return format;
        // GL_BGR and GL_BGRA are not supported in ES 3.2 — remap to RGB/RGBA
        // and handle byte swizzling via texture swizzle mask if needed
        return switch (format) {
            case 0x80E0 /* GL_BGR  */ -> 0x1907; // GL_RGB
            case 0x80E1 /* GL_BGRA */ -> 0x1908; // GL_RGBA
            default -> format;
        };
    }
}

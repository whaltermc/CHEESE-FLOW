package dev.amethyst.glesbackend.gl;

import dev.amethyst.glesbackend.GLESBackendMod;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * Maps desktop OpenGL internal formats and enumerants to their
 * OpenGL ES 3.2 equivalents.
 *
 * Also provides capability queries to detect what's available
 * on the current ES driver.
 */
public final class GLESCompat {

    private GLESCompat() {}

    // -------------------------------------------------------------------------
    // Internal format mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a desktop GL internal format to an ES 3.2 safe equivalent.
     *
     * ES 3.2 requires sized internal formats — unsized formats like GL_RGB,
     * GL_RGBA are only valid as texture base formats, not as internal formats
     * for glTexImage2D in a strict ES context (though many drivers accept them).
     *
     * We enforce sized formats to be safe across all ES 3.2 drivers.
     */
    public static int mapInternalFormat(int desktopFormat) {
        if (!GLESBackendMod.isGLESContext) return desktopFormat;

        return switch (desktopFormat) {
            // Unsized → sized equivalents
            case 0x1907 /* GL_RGB  */ -> GL30.GL_RGB8;
            case 0x1908 /* GL_RGBA */ -> GL30.GL_RGBA8;
            case 0x1906 /* GL_ALPHA */ -> GL30.GL_R8; // closest ES equivalent; shader may need adjusting

            // Depth formats — GL_DEPTH_COMPONENT is unsized in ES, use sized
            case 0x1902 /* GL_DEPTH_COMPONENT */ -> GL30.GL_DEPTH_COMPONENT24;

            // Depth+stencil
            case 0x84F9 /* GL_DEPTH_STENCIL */ -> GL30.GL_DEPTH24_STENCIL8;

            // Already sized — pass through
            default -> desktopFormat;
        };
    }

    // -------------------------------------------------------------------------
    // GL_POLYGON_MODE replacement
    // -------------------------------------------------------------------------

    // glPolygonMode does not exist in OpenGL ES. Wireframe rendering
    // would need geometry shader or manual line drawing — for now we no-op it.
    // This is called from the RenderSystemMixin stubs.
    public static void polygonMode(int face, int mode) {
        // No-op on GLES — wireframe not supported without GS workaround
        // Future: implement via geometry shader pass or GL_LINES draw calls
        if (!GLESBackendMod.isGLESContext) {
            // On desktop, delegate to real GL (though the mixin shouldn't call this path)
            org.lwjgl.opengl.GL11.glPolygonMode(face, mode);
        }
    }

    // -------------------------------------------------------------------------
    // Depth range — ES 3.2 uses glDepthRangef (float), not glDepthRange (double)
    // LWJGL's GL11.glDepthRange maps to double; make sure we route to float version
    // -------------------------------------------------------------------------

    public static void depthRange(double near, double far) {
        if (GLESBackendMod.isGLESContext) {
            org.lwjgl.opengles.GLES20.glDepthRangef((float) near, (float) far);
        } else {
            org.lwjgl.opengl.GL11.glDepthRange(near, far);
        }
    }

    // -------------------------------------------------------------------------
    // Line width — ES 3.2 only guarantees width = 1.0; wider lines are optional
    // -------------------------------------------------------------------------

    public static void lineWidth(float width) {
        if (GLESBackendMod.isGLESContext) {
            // Clamp to 1.0 since wide lines are not guaranteed on ES
            org.lwjgl.opengles.GLES20.glLineWidth(1.0f);
        } else {
            org.lwjgl.opengl.GL11.glLineWidth(width);
        }
    }

    // -------------------------------------------------------------------------
    // Texture filtering — ES 3.2 supports everything desktop does for 2D/3D/cube
    // but double-check mipmap completeness requirements are met
    // -------------------------------------------------------------------------

    /**
     * ES 3.2 requires textures to be mipmap-complete if using mipmap filters.
     * Minecraft usually handles this, but we can force base level limits
     * on textures that aren't going to have mipmaps generated.
     */
    public static void ensureMipmapCompleteness(int textureId, boolean hasMipmaps) {
        if (!GLESBackendMod.isGLESContext) return;

        if (!hasMipmaps) {
            // Lock the texture to base level only so ES doesn't consider it incomplete
            org.lwjgl.opengles.GLES30.glTexParameteri(
                    org.lwjgl.opengles.GLES20.GL_TEXTURE_2D,
                    org.lwjgl.opengles.GLES30.GL_TEXTURE_BASE_LEVEL, 0);
            org.lwjgl.opengles.GLES30.glTexParameteri(
                    org.lwjgl.opengles.GLES20.GL_TEXTURE_2D,
                    org.lwjgl.opengles.GLES30.GL_TEXTURE_MAX_LEVEL, 0);
        }
    }
}

package dev.amethyst.glesbackend.gl;

import dev.amethyst.glesbackend.GLESBackendMod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocesses Minecraft's desktop GLSL shaders for OpenGL ES 3.2 compatibility.
 *
 * Handles:
 *  - Version directive replacement (#version 150/330/core → #version 320 es)
 *  - Precision qualifier injection (required by GLSL ES)
 *  - Removal/stubbing of desktop-only features (glPolygonMode etc. are handled in mixins)
 *  - Texture function compatibility
 */
public final class GLESPreprocessor {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("#version\\s+(\\d+)(?:\\s+(core|compatibility|es))?");

    /**
     * Full precision header injected after the version directive.
     * ES 3.2 requires explicit precision for all types.
     */
    private static final String PRECISION_HEADER =
            "precision highp float;\n" +
            "precision highp int;\n" +
            "precision highp sampler2D;\n" +
            "precision highp sampler2DArray;\n" +
            "precision highp sampler3D;\n" +
            "precision highp samplerCube;\n" +
            "precision highp samplerCubeShadow;\n" +
            "precision highp sampler2DShadow;\n" +
            "precision highp sampler2DArrayShadow;\n" +
            "precision highp isampler2D;\n" +
            "precision highp isampler3D;\n" +
            "precision highp isamplerCube;\n" +
            "precision highp isampler2DArray;\n" +
            "precision highp usampler2D;\n" +
            "precision highp usampler3D;\n" +
            "precision highp usamplerCube;\n" +
            "precision highp usampler2DArray;\n";

    private GLESPreprocessor() {}

    /**
     * Main entry point. Takes a raw GLSL source string and returns ES 3.2 compatible source.
     *
     * @param source   raw GLSL source from Minecraft's shader files
     * @param isVertex true for vertex shaders, false for fragment shaders
     * @return patched GLSL source ready for glShaderSource on an ES 3.2 context
     */
    public static String process(String source, boolean isVertex) {
        if (!GLESBackendMod.isGLESContext) return source;

        source = patchVersionDirective(source);
        source = injectPrecisionHeader(source);
        source = patchBuiltins(source, isVertex);
        source = patchExtensions(source);

        return source;
    }

    /**
     * Replaces any desktop version directive with #version 320 es.
     *
     * Minecraft uses #version 150 (GL 3.2) and #version 330 (GL 3.3) shaders.
     * Both map cleanly to GLSL ES 3.20 in terms of feature set.
     */
    private static String patchVersionDirective(String source) {
        Matcher matcher = VERSION_PATTERN.matcher(source);
        if (matcher.find()) {
            String existing = matcher.group(0);
            if (!existing.contains("es")) {
                source = source.replace(existing, "#version 320 es");
            }
        } else {
            // No version directive at all — prepend ES 3.2
            source = "#version 320 es\n" + source;
        }
        return source;
    }

    /**
     * Injects precision qualifiers immediately after the version directive.
     * These are mandatory in GLSL ES and absent from desktop GLSL.
     */
    private static String injectPrecisionHeader(String source) {
        // Find end of version line and inject precision block right after
        int versionEnd = source.indexOf('\n');
        if (versionEnd == -1) {
            return source + "\n" + PRECISION_HEADER;
        }
        return source.substring(0, versionEnd + 1)
                + PRECISION_HEADER
                + source.substring(versionEnd + 1);
    }

    /**
     * Patches an included GLSL chunk (from #moj_import).
     * These chunks don't have a version directive — only fix built-ins and extensions.
     */
    public static String processInclude(String source, boolean isVertex) {
        if (!GLESBackendMod.isGLESContext) return source;
        source = patchBuiltins(source, isVertex);
        source = patchExtensions(source);
        return source;
    }

    /**
     * Patches GLSL built-ins that differ between desktop and ES.
     *
     * Key differences in 1.20 shaders:
     *  - gl_FragColor is removed in core profile / ES (use out declarations instead)
     *  - texture2D() deprecated → texture()  (Minecraft already uses texture() in 1.20, but check)
     *  - textureLod(), textureOffset() etc. are all fine in ES 3.2
     */
    private static String patchBuiltins(String source, boolean isVertex) {
        // gl_FragColor was already removed in Minecraft 1.16+ shaders
        // but patch it defensively in case any older compat shader slips through
        if (!isVertex && source.contains("gl_FragColor")) {
            // Inject output declaration if missing
            if (!source.contains("out vec4")) {
                source = source.replace(
                        PRECISION_HEADER,
                        PRECISION_HEADER + "out vec4 fragColor;\n"
                );
            }
            source = source.replace("gl_FragColor", "fragColor");
        }

        // texture2D / texture3D / textureCube → texture() in ES 3.x
        source = source.replaceAll("\\btexture2D\\b", "texture");
        source = source.replaceAll("\\btexture3D\\b", "texture");
        source = source.replaceAll("\\btextureCube\\b", "texture");
        source = source.replaceAll("\\btexture2DLod\\b", "textureLod");
        source = source.replaceAll("\\btextureCubeLod\\b", "textureLod");

        return source;
    }

    /**
     * Removes or stubs desktop-specific extensions that don't exist in ES.
     *
     * Extensions present in Minecraft shaders that need handling:
     *  - GL_ARB_explicit_attrib_location  → supported natively in GLSL ES 3.x, just remove directive
     *  - GL_ARB_shader_bit_encoding       → built-in in ES 3.x
     *  - GL_EXT_gpu_shader4               → functionality is core in ES 3.x
     */
    private static String patchExtensions(String source) {
        // These are core in ES 3.2 — remove the extension enable directives
        source = source.replaceAll(
                "#extension\\s+GL_ARB_explicit_attrib_location\\s*:\\s*\\w+\\s*\n?", "");
        source = source.replaceAll(
                "#extension\\s+GL_ARB_shader_bit_encoding\\s*:\\s*\\w+\\s*\n?", "");
        source = source.replaceAll(
                "#extension\\s+GL_EXT_gpu_shader4\\s*:\\s*\\w+\\s*\n?", "");

        // OES extensions that ARE available on GLES — keep or translate
        // GL_OES_standard_derivatives → core in ES 3.x, remove the extension line
        source = source.replaceAll(
                "#extension\\s+GL_OES_standard_derivatives\\s*:\\s*\\w+\\s*\n?", "");

        return source;
    }
}

package dev.amethyst.glesbackend.mixin;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import dev.amethyst.glesbackend.GLESBackendMod;
import dev.amethyst.glesbackend.gl.GLESPreprocessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.InputStream;

/**
 * Intercepts shader source compilation in Blaze3D's Program class.
 *
 * Blaze3D compiles shaders through:
 *   Program.compileShader(Type, int, InputStream, String, GlslPreprocessor)
 *
 * The GlslPreprocessor handles #moj_import resolution before the source
 * reaches glShaderSource. We inject our ES patching into the preprocessor
 * output by wrapping it.
 */
@Mixin(Program.class)
public abstract class ProgramMixin {

    /**
     * Wraps the GlslPreprocessor passed to compileShader so that our
     * GLESPreprocessor runs on each source chunk after #moj_import resolution.
     *
     * We target the preprocessor parameter specifically so we don't disrupt
     * the import resolution pass — only the final source output.
     */
    @ModifyVariable(
            method = "compileShader",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static GlslPreprocessor wrapPreprocessor(
            GlslPreprocessor original,
            Program.Type type,
            int existingId,
            InputStream shaderData,
            String sourceName,
            GlslPreprocessor preprocessor
    ) {
        if (!GLESBackendMod.isGLESContext) return original;

        boolean isVertex = type == Program.Type.VERTEX;

        // Wrap the original preprocessor: first let it handle #moj_import,
        // then patch each resulting chunk for ES compatibility.
        return new GlslPreprocessor() {
            @Override
            public java.util.List<String> process(String source) {
                java.util.List<String> chunks = original.process(source);
                java.util.List<String> patched = new java.util.ArrayList<>(chunks.size());

                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    // Only process the first chunk which contains the version directive.
                    // Subsequent chunks are #moj_import includes — patch them for
                    // precision/built-in fixes but skip version replacement.
                    if (i == 0) {
                        chunk = GLESPreprocessor.process(chunk, isVertex);
                    } else {
                        chunk = GLESPreprocessor.processInclude(chunk, isVertex);
                    }
                    patched.add(chunk);
                }

                GLESBackendMod.LOGGER.debug(
                        "[GLES Backend] Patched shader '{}' ({}) for ES 3.2",
                        sourceName, type.getName()
                );

                return patched;
            }
        };
    }
}

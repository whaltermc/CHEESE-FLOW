package dev.amethyst.glesbackend;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class GLESBackendMod implements ClientModInitializer {

    public static final String MOD_ID = "glesbackend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Set by Amethyst before Minecraft initializes — tells us GLES is available
    public static boolean isGLESContext = false;

    @Override
    public void onInitializeClient() {
        // Detect if we're running under Amethyst's GLES context
        // Amethyst sets this system property before launching Minecraft
        String contextType = System.getProperty("amethyst.gl.context", "desktop");
        isGLESContext = contextType.equals("gles");

        if (isGLESContext) {
            LOGGER.info("[GLES Backend] OpenGL ES 3.2 context detected — activating Blaze3D patches");
        } else {
            LOGGER.warn("[GLES Backend] No GLES context detected — patches will be skipped (desktop fallback)");
        }
    }
}

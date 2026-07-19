package shit.zen.render;

public final class BlurRenderer {
    public static void ensureInitialized() {
        // The old OpenGL blur path cannot be mixed with the 1.21.8 render graph.
    }

    public static void renderBlur(DrawContext drawContext, float x, float y, float width, float height, float radius, Runnable drawCallback) {
        drawCallback.run();
    }

    public static void cleanup() {
    }
}

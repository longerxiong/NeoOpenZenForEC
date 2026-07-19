package shit.zen.render;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import shit.zen.ClientBase;

public class Renderer
extends ClientBase {
    private static float guiScale = 1.0f;
    private static boolean verified = false;
    private static DrawContext currentCanvas;

    public static DrawContext getCanvas() {
        return currentCanvas;
    }

    public static float getGuiScale() {
        return guiScale;
    }

    public static void verify() {
        verified = true;
    }

    public static void updateGuiScale() {
        Renderer.setGuiScale((float)mc.getWindow().getGuiScale());
    }

    public static void setGuiScale(float scale) {
        guiScale = scale;
        Renderer.verify();
    }

    public static void resetPixelStore() {
    }

    public static void resetRenderState() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void render(GuiGraphics guiGraphics, Consumer<DrawContext> consumer) {
        if (!verified) {
            Renderer.verify();
            if (!verified) {
                return;
            }
        }
        if (currentCanvas != null) {
            consumer.accept(currentCanvas);
            return;
        }
        DrawContext drawContext = new DrawContext(guiGraphics);
        DrawContext previousCanvas = currentCanvas;
        currentCanvas = drawContext;
        try {
            consumer.accept(drawContext);
        } finally {
            currentCanvas = previousCanvas;
            drawContext.clearClipStack();
        }
    }

    public static void renderConsumer(Consumer<DrawContext> consumer) {
        if (currentCanvas != null) {
            consumer.accept(currentCanvas);
            return;
        }
        Renderer.render(null, consumer);
    }

    public static void setGuiScaleVerified(float scale) {
        Renderer.setGuiScale(scale);
    }
}

package shit.zen.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.opengl.GL11;

public final class StencilHelper {
    private StencilHelper() {
    }

    public static void applyStencil(PoseStack poseStack, Runnable drawMask, Runnable drawContent, float opacity) {
        drawContent.run();
    }

    public static void beginWrite(boolean keepColor) {
        if (!keepColor) {
            GL11.glColorMask(false, false, false, false);
        }
    }

    public static void beginWriteFull(boolean keepColor, RenderTarget renderTarget, boolean clearStencil, boolean invertMask) {
        beginWrite(keepColor);
    }

    public static void beginRead(boolean inside) {
        GL11.glColorMask(true, true, true, true);
    }

    public static void end() {
        GL11.glColorMask(true, true, true, true);
    }

    public static void setupFBO(RenderTarget renderTarget) {
    }

    public static void attachStencilBuffer(RenderTarget renderTarget) {
    }
}

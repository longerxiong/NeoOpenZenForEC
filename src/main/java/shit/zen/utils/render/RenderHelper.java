package shit.zen.utils.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import lombok.Generated;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

public final class RenderHelper {
    public static void blitRenderTarget(RenderTarget renderTarget, PoseStack poseStack, int width, int height) {
        // Legacy framebuffer blitting is not compatible with the 1.21.8 render graph.
    }

    public static void blitRenderTargetSafe(RenderTarget renderTarget, PoseStack poseStack, int width, int height) {
        blitRenderTarget(renderTarget, poseStack, width, height);
    }

    public static void setTexFilter(int minFilter, int magFilter) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
    }

    public static void pushScaleAround(PoseStack poseStack, float pivotX, float pivotY, float scale) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    public static void popPose(PoseStack poseStack) {
        poseStack.popPose();
    }

    public static void pushRotateAround(PoseStack poseStack, float pivotX, float pivotY, float angleDegrees) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    public static void resetShaderColor() {
    }

    public static void setShaderColorRGBA(int r, int g, int b, int a) {
    }

    public static void setShaderColorWithAlpha(int color, int alpha) {
    }

    public static void setShaderColor(int color) {
    }

    public static void withBlend(Runnable runnable) {
        runnable.run();
    }

    public static void setShaderColorComponents(int color) {
        setShaderColorRGBA(ColorUtil.getRed(color), ColorUtil.getGreen(color), ColorUtil.getBlue(color), ColorUtil.getAlpha(color));
    }

    public static DynamicTexture uploadTexture(NativeImage nativeImage, BufferedImage bufferedImage) {
        for (int i = 0; i < bufferedImage.getWidth(); ++i) {
            for (int j = 0; j < bufferedImage.getHeight(); ++j) {
                nativeImage.setPixel(i, j, bufferedImage.getRGB(i, j));
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "openzen/uploaded_texture", nativeImage);
        texture.upload();
        return texture;
    }

    @Generated
    private RenderHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

package shit.zen.utils.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import lombok.Generated;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import shit.zen.ClientBase;
import shit.zen.render.DrawContext;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.Renderer;
import shit.zen.render.ResourceLocationWrapper;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.game.EntityUtil;

public final class RenderUtil extends ClientBase {
    public static class ShadowTexture {
        public final ResourceLocationWrapper resourceLocation = new ResourceLocationWrapper(
                "texture/remote/" + org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(16));

        public ShadowTexture(BufferedImage image) {
            RenderUtil.registerTexture(this.resourceLocation, image);
        }

        public void bind() {
            // Textures are selected by each queued GUI render state in 1.21.8.
        }
    }

    private static final Stack<int[]> scissorStack = new Stack<>();
    private static float zLevel;

    private static void withCanvas(PoseStack poseStack, Consumer<DrawContext> draw) {
        DrawContext canvas = Renderer.getCanvas();
        if (canvas == null) {
            return;
        }
        canvas.save();
        try {
            if (poseStack != null) {
                Matrix4f matrix = poseStack.last().pose();
                canvas.getPoseStack().mul(new Matrix3x2f(
                        matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31()));
            }
            draw.accept(canvas);
        } finally {
            canvas.restore();
        }
    }

    public static void drawGradientV(PoseStack poseStack, float x, float y, float width, float height, int colorTop, int colorBottom) {
        withCanvas(poseStack, canvas -> canvas.drawRectXYWH(x, y, width, height,
                new Paint().setGradCoords(new Paint.GradientCoords(x, y, x, y + height, colorTop, colorBottom))));
    }

    public static void drawGradientH(PoseStack poseStack, float x, float y, float width, float height, int colorLeft, int colorRight) {
        withCanvas(poseStack, canvas -> canvas.drawRectXYWH(x, y, width, height,
                new Paint().setGradCoords(new Paint.GradientCoords(x, y, x + width, y, colorLeft, colorRight))));
    }

    public static void drawDiamond(PoseStack poseStack, float centerX, float centerY, float size, float widthRatio, float bottomRatio, int color) {
        withCanvas(poseStack, canvas -> {
            Path path = new Path()
                    .moveTo(centerX, centerY)
                    .lineTo(centerX - size / widthRatio, centerY + size)
                    .lineTo(centerX, centerY + size / bottomRatio)
                    .lineTo(centerX + size / widthRatio, centerY + size)
                    .closePath();
            canvas.drawPath(path, new Paint().setColor(color));
        });
    }

    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height, float radius, int color) {
        drawRoundedRect(poseStack, x, y, width, height, radius, 1.0f, color);
    }

    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height, float radius, float smoothness, int color) {
        withCanvas(poseStack, canvas -> canvas.drawRoundedRect(
                RoundedRectangle.ofXYWHR(x, y, width, height, radius), new Paint().setColor(color)));
    }

    public static void drawRoundedRectCorners(PoseStack poseStack, float x, float y, float width, float height, float radius,
                                               boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight, int color) {
        float[] radii = {topLeft ? radius : 0.0f, topRight ? radius : 0.0f,
                bottomRight ? radius : 0.0f, bottomLeft ? radius : 0.0f};
        withCanvas(poseStack, canvas -> canvas.drawRoundedRect(
                RoundedRectangle.ofXYWHRadii(x, y, width, height, radii), new Paint().setColor(color)));
    }

    public static void drawBlurredRect(PoseStack poseStack, float x, float y, float width, float height,
                                       float radius, float blurRadius, float opacity, int color) {
        if (color == 0) {
            return;
        }
        int tinted = ColorUtil.withAlpha(color, opacity);
        withCanvas(poseStack, canvas -> canvas.drawRoundedRect(
                RoundedRectangle.ofXYWHR(x, y, width, height, radius), new Paint().setColor(tinted)));
    }

    public static void pushScissor(int x, int y, int width, int height) {
        int clippedX = x;
        int clippedY = y;
        int clippedRight = x + Math.max(0, width);
        int clippedBottom = y + Math.max(0, height);
        if (!scissorStack.isEmpty()) {
            int[] parent = scissorStack.peek();
            clippedX = Math.max(clippedX, parent[0]);
            clippedY = Math.max(clippedY, parent[1]);
            clippedRight = Math.min(clippedRight, parent[2]);
            clippedBottom = Math.min(clippedBottom, parent[3]);
        }
        int[] bounds = {clippedX, clippedY, Math.max(clippedX, clippedRight), Math.max(clippedY, clippedBottom)};
        scissorStack.push(bounds);
        DrawContext canvas = Renderer.getCanvas();
        if (canvas != null) {
            canvas.getGuiGraphics().enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    public static void popScissor() {
        if (!scissorStack.isEmpty()) {
            scissorStack.pop();
        }
        DrawContext canvas = Renderer.getCanvas();
        if (canvas == null) {
            return;
        }
        if (scissorStack.isEmpty()) {
            canvas.getGuiGraphics().disableScissor();
        } else {
            int[] bounds = scissorStack.peek();
            canvas.getGuiGraphics().enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    public static void drawTexturedRect(PoseStack poseStack, int x, int y, int width, int height,
                                        int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        // The old method depended on an implicit globally bound texture, which no longer exists.
    }

    public static void drawFilledRect(PoseStack poseStack, float x, float y, float width, float height, int color) {
        withCanvas(poseStack, canvas -> canvas.drawRectXYWH(x, y, width, height, new Paint().setColor(color)));
    }

    public static void drawFilledRect(PoseStack poseStack, float x, float y, float width, float height) {
        drawFilledRect(poseStack, x, y, width, height, -1);
    }

    public static void drawSolidBox(AABB box, PoseStack poseStack) {
        drawSolidBox(box, poseStack, Color.WHITE, 1.0f);
    }

    public static void drawSolidBox(AABB box, PoseStack poseStack, Color color, float alpha) {
        float red = color.getRed() / 255.0f;
        float green = color.getGreen() / 255.0f;
        float blue = color.getBlue() / 255.0f;
        WorldOverlayRenderer.drawSolidBox(box, poseStack, red, green, blue, alpha);
    }

    public static void drawOutlineBox(AABB box, PoseStack poseStack) {
        drawOutlineBox(box, poseStack, Color.WHITE, 1.0f);
    }

    public static void drawOutlineBox(AABB box, PoseStack poseStack, Color color, float alpha) {
        float red = color.getRed() / 255.0f;
        float green = color.getGreen() / 255.0f;
        float blue = color.getBlue() / 255.0f;
        WorldOverlayRenderer.drawOutlineBox(box, poseStack, red, green, blue, alpha);
    }

    public static boolean isHovered(float x, float y, float width, float height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static void drawQuad(BufferBuilder bufferBuilder, Matrix4f matrix, float left, float top, float right, float bottom, Color color) {
        bufferBuilder.addVertex(matrix, left, bottom, 0.0f).setColor(color.getRGB());
        bufferBuilder.addVertex(matrix, right, bottom, 0.0f).setColor(color.getRGB());
        bufferBuilder.addVertex(matrix, right, top, 0.0f).setColor(color.getRGB());
        bufferBuilder.addVertex(matrix, left, top, 0.0f).setColor(color.getRGB());
    }

    public static void drawSpiralEffect(PoseStack poseStack, Entity entity, float partialTicks) {
        if (mc == null || entity == null) {
            return;
        }
        Vec3 position = EntityUtil.getInterpolatedPos(entity, partialTicks);
        double cycle = System.currentTimeMillis() % 2000L / 1000.0;
        double progress = cycle > 1.0 ? 2.0 - cycle : cycle;
        double y = position.y + entity.getBbHeight() * progress;
        double radius = entity.getBbWidth() * 0.4875;
        org.joml.Vector3f view = new org.joml.Vector3f();
        WorldOverlayRenderer.withCameraModelView(() -> {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer buffer = buffers.getBuffer(RenderType.lines());
            int segments = 60;
            for (int i = 0; i < segments; i++) {
                double angle1 = Math.PI * 2.0 * i / segments;
                double angle2 = Math.PI * 2.0 * (i + 1) / segments;
                double wx1 = position.x + Math.cos(angle1) * radius;
                double wz1 = position.z + Math.sin(angle1) * radius;
                double wx2 = position.x + Math.cos(angle2) * radius;
                double wz2 = position.z + Math.sin(angle2) * radius;
                WorldOverlayRenderer.toCameraRelative(wx1, y, wz1, view);
                float x1 = view.x, y1 = view.y, z1 = view.z;
                WorldOverlayRenderer.toCameraRelative(wx2, y, wz2, view);
                float x2 = view.x, y2 = view.y, z2 = view.z;
                float normalX = x2 - x1;
                float normalZ = z2 - z1;
                float length = Math.max(1.0E-4f, (float)Math.hypot(normalX, normalZ));
                normalX /= length;
                normalZ /= length;
                int color1 = ColorUtil.getRainbowColor(10, i * 5).getRGB();
                int color2 = ColorUtil.getRainbowColor(10, (i + 1) * 5).getRGB();
                buffer.addVertex(x1, y1, z1).setColor(color1).setNormal(normalX, 0.0f, normalZ);
                buffer.addVertex(x2, y2, z2).setColor(color2).setNormal(normalX, 0.0f, normalZ);
            }
            buffers.endBatch(RenderType.lines());
        });
    }

    public static void drawColoredBox(AABB box, PoseStack poseStack, Color topColor, Color bottomColor) {
        WorldOverlayRenderer.drawOutlineBox(
                box,
                poseStack,
                bottomColor.getRed() / 255.0f,
                bottomColor.getGreen() / 255.0f,
                bottomColor.getBlue() / 255.0f,
                bottomColor.getAlpha() / 255.0f,
                topColor.getRed() / 255.0f,
                topColor.getGreen() / 255.0f,
                topColor.getBlue() / 255.0f);
    }

    public static void drawFilledColoredBox(AABB box, PoseStack poseStack, Color topColor, Color bottomColor) {
        drawSolidBox(box, poseStack, bottomColor, bottomColor.getAlpha() / 255.0f);
    }

    public static void drawBoxVerts(BufferBuilder bufferBuilder, Matrix4f matrix, AABB box) {
        // BufferBuilder construction is owned by RenderPipeline in 1.21.8; retained for binary compatibility.
    }

    public static void enableBlend() {
    }

    public static void disableBlend() {
    }

    public static void drawTexture(ResourceLocation resourceLocation, PoseStack poseStack, float x, float y,
                                   float width, float height, float alpha, int color) {
        int tinted = ColorUtil.withAlpha(color, alpha);
        withCanvas(poseStack, canvas -> canvas.drawRoundedTexture(resourceLocation,
                RoundedRectangle.ofXYWHR(x, y, width, height, 0.0f), tinted, 0.0f, 0.0f, 1.0f, 1.0f));
    }

    public static void drawTexture(GpuTextureView texture, PoseStack poseStack, float x, float y,
                                   float width, float height, float alpha, int color) {
        int tinted = ColorUtil.withAlpha(color, alpha);
        withCanvas(poseStack, canvas -> canvas.drawTexture(texture, Rectangle.ofXYWH(x, y, width, height), tinted));
    }

    public static void drawShadow(PoseStack poseStack, float x, float y, float width, float height, int blurRadius, int color) {
        withCanvas(poseStack, canvas -> canvas.drawBlurredRoundedRect(
                RoundedRectangle.ofXYWHR(x, y, width, height, Math.min(width, height) * 0.25f),
                0.0f, 0.0f, blurRadius, 0.0f, color));
    }

    public static void registerTexture(ResourceLocationWrapper resourceLocationWrapper, BufferedImage bufferedImage) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", output);
            registerTextureBytes(resourceLocationWrapper, output.toByteArray());
        } catch (Exception exception) {
            logger.error("Failed to encode texture {}", resourceLocationWrapper.get(), exception);
        }
    }

    private static void registerTextureBytes(ResourceLocationWrapper resourceLocationWrapper, byte[] pngBytes) {
        try {
            ByteBuffer buffer = BufferUtils.createByteBuffer(pngBytes.length).put(pngBytes);
            buffer.flip();
            DynamicTexture texture = new DynamicTexture(
                    () -> "openzen/" + resourceLocationWrapper.get(), NativeImage.read(buffer));
            mc.execute(() -> mc.getTextureManager().register(resourceLocationWrapper.get(), texture));
        } catch (Exception exception) {
            logger.error("Failed to register texture {}", resourceLocationWrapper.get(), exception);
        }
    }

    public static int lerpColorHSB(int colorA, int colorB, float progress) {
        float[] hsbA = Color.RGBtoHSB(colorA >> 16 & 0xFF, colorA >> 8 & 0xFF, colorA & 0xFF, null);
        float[] hsbB = Color.RGBtoHSB(colorB >> 16 & 0xFF, colorB >> 8 & 0xFF, colorB & 0xFF, null);
        float hue = hsbA[0] + (hsbB[0] - hsbA[0]) * progress;
        float saturation = hsbA[1] + (hsbB[1] - hsbA[1]) * progress;
        float brightness = hsbA[2] + (hsbB[2] - hsbA[2]) * progress;
        int alpha = (int)((colorA >>> 24) + ((colorB >>> 24) - (colorA >>> 24)) * progress);
        return alpha << 24 | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    public static void setZLevel(float zLevelValue) {
        zLevel = zLevelValue;
    }

    @Generated
    private RenderUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

package shit.zen.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public class DrawContext {
    private record Vertex(float x, float y, float u, float v, int color) {
    }

    private record GeometryRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            List<Vertex> vertices,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds
    ) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer, float z) {
            boolean textured = this.textureSetup.texure0() != null;
            for (Vertex vertex : this.vertices) {
                VertexConsumer target = consumer.addVertexWith2DPose(this.pose, vertex.x, vertex.y, z);
                if (textured) {
                    target.setUv(vertex.u, vertex.v);
                }
                target.setColor(vertex.color);
            }
        }
    }

    public static final class StrokeState {
        private final Paint.LinearGradient gradient;
        private float dashOffset;
        private boolean inDash = true;

        StrokeState(Paint.LinearGradient gradient) {
            this.gradient = gradient;
            if (gradient != null) {
                this.dashOffset = gradient.angle;
            }
        }

        boolean isDrawing() {
            return this.gradient == null || this.inDash;
        }

        void advance(float length) {
            if (this.gradient == null || this.gradient.colors == null || this.gradient.colors.length == 0) {
                return;
            }
            this.dashOffset += length;
            while (this.dashOffset >= this.currentDashLength()) {
                this.dashOffset -= this.currentDashLength();
                this.inDash = !this.inDash;
            }
        }

        private float currentDashLength() {
            int index = this.inDash ? 0 : (this.gradient.colors.length > 1 ? 1 : 0);
            return Math.max(0.001f, this.gradient.colors[index]);
        }
    }

    @Getter
    private final GuiGraphics guiGraphics;
    @Getter
    private final Matrix3x2fStack poseStack;
    private final Deque<Boolean> clipStack = new ArrayDeque<>();

    public DrawContext(GuiGraphics guiGraphics) {
        if (guiGraphics == null) {
            throw new IllegalArgumentException("GuiGraphics is required for GUI rendering");
        }
        this.guiGraphics = guiGraphics;
        this.poseStack = guiGraphics.pose();
    }

    public void save() {
        this.poseStack.pushMatrix();
        this.clipStack.push(Boolean.FALSE);
    }

    public void restore() {
        if (this.clipStack.isEmpty()) {
            throw new IllegalStateException("DrawContext restore without matching save");
        }
        if (this.clipStack.pop()) {
            this.guiGraphics.disableScissor();
        }
        this.poseStack.popMatrix();
    }

    public void translate(float x, float y) {
        this.poseStack.translate(x, y);
    }

    public void scale(float scaleX, float scaleY) {
        this.poseStack.scale(scaleX, scaleY);
    }

    public void rotate(float degrees) {
        this.poseStack.rotate((float)Math.toRadians(degrees));
    }

    public void flush() {
        this.guiGraphics.nextStratum();
    }

    public void clip(Rectangle rectangle) {
        this.clipRect(rectangle, true);
    }

    public void clipRect(Rectangle rectangle, boolean enable) {
        if (!enable) {
            this.guiGraphics.disableScissor();
            return;
        }
        this.guiGraphics.enableScissor(
                (int)Math.floor(rectangle.getX()),
                (int)Math.floor(rectangle.getY()),
                (int)Math.ceil(rectangle.getRight()),
                (int)Math.ceil(rectangle.getBottom()));
        this.markClipOnCurrentSave();
    }

    public void clipRoundedRect(RoundedRectangle rectangle, boolean enable) {
        this.clipRect(Rectangle.ofXYWH(rectangle.x1, rectangle.y1, rectangle.getWidth(), rectangle.getHeight()), enable);
    }

    private void markClipOnCurrentSave() {
        if (!this.clipStack.isEmpty()) {
            this.clipStack.pop();
            this.clipStack.push(Boolean.TRUE);
        }
    }

    public void drawRect(Rectangle rectangle, Paint paint) {
        this.drawRectXYWH(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight(), paint);
    }

    public void drawRectXYWH(float x, float y, float width, float height, Paint paint) {
        if (paint.getCapStyle() == Paint.StrokeCap.STROKE) {
            this.drawRectStroke(x, y, width, height, paint);
            return;
        }
        int topColor = paint.getColor();
        int bottomColor = topColor;
        Paint.GradientCoords gradient = paint.getGradCoords();
        boolean horizontal = false;
        if (gradient != null) {
            topColor = gradient.color1;
            bottomColor = gradient.color2;
            horizontal = Math.abs(gradient.x2 - gradient.x1) > Math.abs(gradient.y2 - gradient.y1);
        }
        List<Vertex> vertices = new ArrayList<>(4);
        if (horizontal) {
            vertices.add(vertex(x, y, topColor));
            vertices.add(vertex(x, y + height, topColor));
            vertices.add(vertex(x + width, y + height, bottomColor));
            vertices.add(vertex(x + width, y, bottomColor));
        } else {
            vertices.add(vertex(x, y, topColor));
            vertices.add(vertex(x, y + height, bottomColor));
            vertices.add(vertex(x + width, y + height, bottomColor));
            vertices.add(vertex(x + width, y, topColor));
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), vertices);
    }

    public void drawRoundedRect(RoundedRectangle rectangle, Paint paint) {
        if (paint.getCapStyle() == Paint.StrokeCap.STROKE) {
            this.drawRoundedRectStroke(rectangle, paint);
            return;
        }
        List<float[]> outline = this.roundedOutline(rectangle);
        this.submitFan(outline, rectangle, paint, null);
    }

    public void drawRoundedTexture(ResourceLocation texture, RoundedRectangle rectangle, int color,
                                   float u1, float v1, float u2, float v2) {
        AbstractTexture abstractTexture = getTexture(texture);
        if (abstractTexture == null || abstractTexture.getTextureView() == null) {
            return;
        }
        List<float[]> outline = this.roundedOutline(rectangle);
        List<Vertex> vertices = new ArrayList<>(outline.size() * 4);
        float centerX = (rectangle.x1 + rectangle.x2) * 0.5f;
        float centerY = (rectangle.y1 + rectangle.y2) * 0.5f;
        Vertex center = texturedVertex(centerX, centerY, lerpUv(centerX, rectangle.x1, rectangle.x2, u1, u2), lerpUv(centerY, rectangle.y1, rectangle.y2, v1, v2), color);
        for (int i = 0; i < outline.size(); i++) {
            float[] a = outline.get(i);
            float[] b = outline.get((i + 1) % outline.size());
            Vertex va = texturedVertex(a[0], a[1], lerpUv(a[0], rectangle.x1, rectangle.x2, u1, u2), lerpUv(a[1], rectangle.y1, rectangle.y2, v1, v2), color);
            Vertex vb = texturedVertex(b[0], b[1], lerpUv(b[0], rectangle.x1, rectangle.x2, u1, u2), lerpUv(b[1], rectangle.y1, rectangle.y2, v1, v2), color);
            addDegenerateTriangle(vertices, center, va, vb);
        }
        this.submit(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(abstractTexture.getTextureView()), vertices);
    }

    public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
        this.drawLineSegment(x1, y1, x2, y2, Math.max(0.5f, paint.getStrokeWidth()), paint.getColor());
    }

    public void drawString(String text, float x, float y, FontRenderer fontRenderer, Paint paint) {
        if (text == null || text.isEmpty() || fontRenderer.getFont() == null) {
            return;
        }
        fontRenderer.getFont().drawString(this, text, x, y + fontRenderer.getMetrics().ascent(), paint.getColor());
    }

    public void drawArc(float x1, float y1, float x2, float y2, float startAngle, float sweepAngle, boolean unused, Paint paint) {
        float centerX = (x1 + x2) * 0.5f;
        float centerY = (y1 + y2) * 0.5f;
        float radius = Math.min(x2 - x1, y2 - y1) * 0.5f;
        int segments = 32;
        if (paint.getCapStyle() == Paint.StrokeCap.STROKE) {
            float previousX = centerX + (float)Math.cos(Math.toRadians(startAngle)) * radius;
            float previousY = centerY + (float)Math.sin(Math.toRadians(startAngle)) * radius;
            for (int i = 1; i <= segments; i++) {
                float angle = startAngle + sweepAngle * i / segments;
                float x = centerX + (float)Math.cos(Math.toRadians(angle)) * radius;
                float y = centerY + (float)Math.sin(Math.toRadians(angle)) * radius;
                this.drawLineSegment(previousX, previousY, x, y, Math.max(0.5f, paint.getStrokeWidth()), paint.getColor());
                previousX = x;
                previousY = y;
            }
            return;
        }
        List<float[]> points = new ArrayList<>(segments + 2);
        points.add(new float[]{centerX, centerY});
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + sweepAngle * i / segments;
            points.add(new float[]{centerX + (float)Math.cos(Math.toRadians(angle)) * radius, centerY + (float)Math.sin(Math.toRadians(angle)) * radius});
        }
        this.submitPolygonFan(points, paint.getColor());
    }

    public void drawPath(Path path, Paint paint) {
        if (path == null) {
            return;
        }
        float currentX = 0.0f;
        float currentY = 0.0f;
        float startX = 0.0f;
        float startY = 0.0f;
        boolean hasCurrent = false;
        List<float[]> polygon = new ArrayList<>();
        StrokeState strokeState = new StrokeState(paint.getLinGradient());
        for (Path.PathSegment segment : path.getSegments()) {
            switch (segment.type) {
                case MOVE_TO -> {
                    this.fillPendingPolygon(polygon, paint);
                    currentX = startX = segment.coords[0];
                    currentY = startY = segment.coords[1];
                    polygon.add(new float[]{currentX, currentY});
                    hasCurrent = true;
                }
                case LINE_TO -> {
                    if (hasCurrent && paint.getCapStyle() != Paint.StrokeCap.FILL) {
                        this.drawStrokedSegment(currentX, currentY, segment.coords[0], segment.coords[1], paint, strokeState);
                    }
                    currentX = segment.coords[0];
                    currentY = segment.coords[1];
                    polygon.add(new float[]{currentX, currentY});
                }
                case QUAD_TO -> {
                    float controlX = segment.coords[0];
                    float controlY = segment.coords[1];
                    float endX = segment.coords[2];
                    float endY = segment.coords[3];
                    float previousX = currentX;
                    float previousY = currentY;
                    for (int i = 1; i <= 24; i++) {
                        float t = i / 24.0f;
                        float inv = 1.0f - t;
                        float x = inv * inv * currentX + 2.0f * inv * t * controlX + t * t * endX;
                        float y = inv * inv * currentY + 2.0f * inv * t * controlY + t * t * endY;
                        if (paint.getCapStyle() != Paint.StrokeCap.FILL) {
                            this.drawStrokedSegment(previousX, previousY, x, y, paint, strokeState);
                        }
                        polygon.add(new float[]{x, y});
                        previousX = x;
                        previousY = y;
                    }
                    currentX = endX;
                    currentY = endY;
                }
                case CUBIC_TO -> {
                    float previousX = currentX;
                    float previousY = currentY;
                    for (int i = 1; i <= 24; i++) {
                        float t = i / 24.0f;
                        float inv = 1.0f - t;
                        float x = inv * inv * inv * currentX + 3.0f * inv * inv * t * segment.coords[0] + 3.0f * inv * t * t * segment.coords[2] + t * t * t * segment.coords[4];
                        float y = inv * inv * inv * currentY + 3.0f * inv * inv * t * segment.coords[1] + 3.0f * inv * t * t * segment.coords[3] + t * t * t * segment.coords[5];
                        if (paint.getCapStyle() != Paint.StrokeCap.FILL) {
                            this.drawStrokedSegment(previousX, previousY, x, y, paint, strokeState);
                        }
                        polygon.add(new float[]{x, y});
                        previousX = x;
                        previousY = y;
                    }
                    currentX = segment.coords[4];
                    currentY = segment.coords[5];
                }
                case CLOSE -> {
                    if (hasCurrent && paint.getCapStyle() != Paint.StrokeCap.FILL) {
                        this.drawStrokedSegment(currentX, currentY, startX, startY, paint, strokeState);
                    }
                    this.fillPendingPolygon(polygon, paint);
                    currentX = startX;
                    currentY = startY;
                }
                case RECT -> this.drawRect(segment.rect, paint);
                case RRECT -> this.drawRoundedRect(segment.roundedRect, paint);
            }
        }
        this.fillPendingPolygon(polygon, paint);
    }

    public void drawTexture(Texture texture, Rectangle srcRect, Rectangle dstRect, Paint paint) {
        if (texture == null || texture.getResourceLocation() == null) {
            return;
        }
        AbstractTexture abstractTexture = getTexture(texture.getResourceLocation());
        if (abstractTexture == null || abstractTexture.getTextureView() == null) {
            return;
        }
        this.guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture.getResourceLocation(),
                Math.round(dstRect.getX()),
                Math.round(dstRect.getY()),
                srcRect.getX(),
                srcRect.getY(),
                Math.max(1, Math.round(dstRect.getWidth())),
                Math.max(1, Math.round(dstRect.getHeight())),
                Math.max(1, Math.round(srcRect.getWidth())),
                Math.max(1, Math.round(srcRect.getHeight())),
                texture.getWidth(),
                texture.getHeight(),
                paint.getColor());
    }

    public void drawTexture(GpuTextureView texture, Rectangle dstRect, int color) {
        if (texture == null) {
            return;
        }
        float x = dstRect.getX();
        float y = dstRect.getY();
        float right = dstRect.getRight();
        float bottom = dstRect.getBottom();
        List<Vertex> vertices = List.of(
                texturedVertex(x, y, 0.0f, 0.0f, color),
                texturedVertex(x, bottom, 0.0f, 1.0f, color),
                texturedVertex(right, bottom, 1.0f, 1.0f, color),
                texturedVertex(right, y, 1.0f, 0.0f, color));
        this.submit(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture), vertices);
    }

    public void drawBlurredRoundedRect(RoundedRectangle rectangle, float offsetX, float offsetY, float blurRadius, float spread, int color) {
        RoundedRectangle expanded = RoundedRectangle.ofXYWHRadii(
                rectangle.x1 + offsetX - spread,
                rectangle.y1 + offsetY - spread,
                rectangle.getWidth() + spread * 2.0f,
                rectangle.getHeight() + spread * 2.0f,
                new float[]{
                        Math.max(0.0f, rectangle.topLeftRadius + spread),
                        Math.max(0.0f, rectangle.topRightRadius + spread),
                        Math.max(0.0f, rectangle.bottomRightRadius + spread),
                        Math.max(0.0f, rectangle.bottomLeftRadius + spread)});
        BlurRenderer.renderBlur(this, expanded.x1, expanded.y1, expanded.getWidth(), expanded.getHeight(), Math.max(0.01f, blurRadius * 0.5f), () -> this.drawRoundedRect(expanded, new Paint().setColor(color)));
    }

    public void drawBlur(float x, float y, float width, float height, float radius, Runnable runnable) {
        BlurRenderer.renderBlur(this, x, y, width, height, radius, runnable);
    }

    void clearClipStack() {
        while (!this.clipStack.isEmpty()) {
            if (this.clipStack.pop()) {
                this.guiGraphics.disableScissor();
            }
            this.poseStack.popMatrix();
        }
    }

    private void drawRectStroke(float x, float y, float width, float height, Paint paint) {
        float stroke = Math.max(0.5f, paint.getStrokeWidth());
        Paint fill = paint.copy().setStrokeCap(Paint.StrokeCap.FILL);
        this.drawRectXYWH(x - stroke * 0.5f, y - stroke * 0.5f, width + stroke, stroke, fill);
        this.drawRectXYWH(x - stroke * 0.5f, y + height - stroke * 0.5f, width + stroke, stroke, fill);
        this.drawRectXYWH(x - stroke * 0.5f, y + stroke * 0.5f, stroke, height - stroke, fill);
        this.drawRectXYWH(x + width - stroke * 0.5f, y + stroke * 0.5f, stroke, height - stroke, fill);
    }

    private void drawRoundedRectStroke(RoundedRectangle rectangle, Paint paint) {
        float stroke = Math.max(0.5f, paint.getStrokeWidth());
        Paint fill = paint.copy().setStrokeCap(Paint.StrokeCap.FILL);
        this.drawRoundedRect(rectangle, fill);
        float inset = stroke;
        if (rectangle.getWidth() <= inset * 2.0f || rectangle.getHeight() <= inset * 2.0f) {
            return;
        }
        RoundedRectangle inner = RoundedRectangle.ofXYWHRadii(
                rectangle.x1 + inset,
                rectangle.y1 + inset,
                rectangle.getWidth() - inset * 2.0f,
                rectangle.getHeight() - inset * 2.0f,
                new float[]{
                        Math.max(0.0f, rectangle.topLeftRadius - inset),
                        Math.max(0.0f, rectangle.topRightRadius - inset),
                        Math.max(0.0f, rectangle.bottomRightRadius - inset),
                        Math.max(0.0f, rectangle.bottomLeftRadius - inset)});
        this.drawRoundedRect(inner, new Paint().setColor(0));
    }

    private List<float[]> roundedOutline(RoundedRectangle rectangle) {
        List<float[]> points = new ArrayList<>(36);
        this.addArc(points, rectangle.x1 + rectangle.topLeftRadius, rectangle.y1 + rectangle.topLeftRadius, rectangle.topLeftRadius, 180.0f, 270.0f);
        this.addArc(points, rectangle.x2 - rectangle.topRightRadius, rectangle.y1 + rectangle.topRightRadius, rectangle.topRightRadius, 270.0f, 360.0f);
        this.addArc(points, rectangle.x2 - rectangle.bottomRightRadius, rectangle.y2 - rectangle.bottomRightRadius, rectangle.bottomRightRadius, 0.0f, 90.0f);
        this.addArc(points, rectangle.x1 + rectangle.bottomLeftRadius, rectangle.y2 - rectangle.bottomLeftRadius, rectangle.bottomLeftRadius, 90.0f, 180.0f);
        return points;
    }

    private void addArc(List<float[]> points, float centerX, float centerY, float radius, float start, float end) {
        if (radius <= 0.0f) {
            points.add(new float[]{centerX, centerY});
            return;
        }
        int segments = Math.max(3, Math.min(12, (int)Math.ceil(radius * 0.75f)));
        for (int i = 0; i <= segments; i++) {
            float angle = (float)Math.toRadians(start + (end - start) * i / segments);
            points.add(new float[]{centerX + (float)Math.cos(angle) * radius, centerY + (float)Math.sin(angle) * radius});
        }
    }

    private void submitFan(List<float[]> outline, RoundedRectangle rectangle, Paint paint, TextureSetup textureSetup) {
        if (outline.size() < 3) {
            return;
        }
        float centerX = (rectangle.x1 + rectangle.x2) * 0.5f;
        float centerY = (rectangle.y1 + rectangle.y2) * 0.5f;
        List<Vertex> vertices = new ArrayList<>(outline.size() * 4);
        Vertex center = vertex(centerX, centerY, this.gradientColor(paint, centerX, centerY, rectangle));
        for (int i = 0; i < outline.size(); i++) {
            float[] a = outline.get(i);
            float[] b = outline.get((i + 1) % outline.size());
            addDegenerateTriangle(vertices, center,
                    vertex(a[0], a[1], this.gradientColor(paint, a[0], a[1], rectangle)),
                    vertex(b[0], b[1], this.gradientColor(paint, b[0], b[1], rectangle)));
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), vertices);
    }

    private int gradientColor(Paint paint, float x, float y, RoundedRectangle rectangle) {
        Paint.GradientCoords gradient = paint.getGradCoords();
        if (gradient == null) {
            return paint.getColor();
        }
        float t;
        if (Math.abs(gradient.x2 - gradient.x1) > Math.abs(gradient.y2 - gradient.y1)) {
            t = (x - rectangle.x1) / Math.max(0.0001f, rectangle.getWidth());
        } else {
            t = (y - rectangle.y1) / Math.max(0.0001f, rectangle.getHeight());
        }
        return lerpColor(gradient.color1, gradient.color2, Math.max(0.0f, Math.min(1.0f, t)));
    }

    private void drawLineSegment(float x1, float y1, float x2, float y2, float strokeWidth, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.hypot(dx, dy);
        if (length < 1.0E-4f) {
            return;
        }
        float normalX = -dy / length * strokeWidth * 0.5f;
        float normalY = dx / length * strokeWidth * 0.5f;
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), List.of(
                vertex(x1 + normalX, y1 + normalY, color),
                vertex(x1 - normalX, y1 - normalY, color),
                vertex(x2 - normalX, y2 - normalY, color),
                vertex(x2 + normalX, y2 + normalY, color)));
    }

    private void drawStrokedSegment(float x1, float y1, float x2, float y2, Paint paint, StrokeState state) {
        if (state.isDrawing()) {
            this.drawLineSegment(x1, y1, x2, y2, Math.max(0.5f, paint.getStrokeWidth()), paint.getColor());
        }
        state.advance((float)Math.hypot(x2 - x1, y2 - y1));
    }

    private void fillPendingPolygon(List<float[]> polygon, Paint paint) {
        if ((paint.getCapStyle() == Paint.StrokeCap.FILL || paint.getCapStyle() == Paint.StrokeCap.STROKE_AND_FILL) && polygon.size() >= 3) {
            this.submitPolygonFan(polygon, paint.getColor());
        }
        polygon.clear();
    }

    private void submitPolygonFan(List<float[]> points, int color) {
        if (points.size() < 3) {
            return;
        }
        float[] centerPoint = points.get(0);
        Vertex center = vertex(centerPoint[0], centerPoint[1], color);
        List<Vertex> vertices = new ArrayList<>((points.size() - 2) * 4);
        for (int i = 1; i < points.size() - 1; i++) {
            float[] a = points.get(i);
            float[] b = points.get(i + 1);
            addDegenerateTriangle(vertices, center, vertex(a[0], a[1], color), vertex(b[0], b[1], color));
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), vertices);
    }

    private void submit(RenderPipeline pipeline, TextureSetup textureSetup, List<Vertex> vertices) {
        if (vertices.isEmpty()) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(this.poseStack);
        ScreenRectangle bounds = calculateBounds(vertices, pose, this.guiGraphics.peekScissorStack());
        if (bounds == null) {
            return;
        }
        this.guiGraphics.submitGuiElementRenderState(new GeometryRenderState(
                pipeline, textureSetup, pose, List.copyOf(vertices), this.guiGraphics.peekScissorStack(), bounds));
    }

    private static ScreenRectangle calculateBounds(List<Vertex> vertices, Matrix3x2f pose, ScreenRectangle scissor) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vertex vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
        }
        ScreenRectangle bounds = new ScreenRectangle(
                (int)Math.floor(minX), (int)Math.floor(minY),
                Math.max(1, (int)Math.ceil(maxX - minX)), Math.max(1, (int)Math.ceil(maxY - minY))).transformMaxBounds(pose);
        return scissor == null ? bounds : scissor.intersection(bounds);
    }

    private static Vertex vertex(float x, float y, int color) {
        return new Vertex(x, y, 0.0f, 0.0f, color);
    }

    private static Vertex texturedVertex(float x, float y, float u, float v, int color) {
        return new Vertex(x, y, u, v, color);
    }

    private static void addDegenerateTriangle(List<Vertex> vertices, Vertex center, Vertex a, Vertex b) {
        vertices.add(center);
        vertices.add(a);
        vertices.add(b);
        vertices.add(b);
    }

    private static float lerpUv(float value, float min, float max, float uvMin, float uvMax) {
        return uvMin + (value - min) / Math.max(0.0001f, max - min) * (uvMax - uvMin);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static AbstractTexture getTexture(ResourceLocation resourceLocation) {
        return Minecraft.getInstance().getTextureManager().getTexture(resourceLocation);
    }
}

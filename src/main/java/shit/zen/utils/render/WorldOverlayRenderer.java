package shit.zen.utils.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import shit.zen.ClientBase;
import shit.zen.render.DrawContext;
import shit.zen.render.Paint;
import shit.zen.render.Renderer;

import java.util.ArrayList;
import java.util.List;

public final class WorldOverlayRenderer extends ClientBase {
    private static boolean active;
    // True once the camera + matrices have been snapshotted for the current
    // frame (from LevelRenderer.renderLevel HEAD). Prevents begin() from
    // re-reading the argument matrices at TAIL, where they may already have
    // been mutated in place by the frame-graph pass.
    private static boolean capturedThisFrame;
    private static float camX, camY, camZ;
    private static final Matrix4f viewProj = new Matrix4f();
    private static final Vector4f ndc = new Vector4f();
    private static final Vector3f temp = new Vector3f();
    private static int screenW, screenH;

    // ========== Deferred world-overlay geometry ==========
    // Box edges are projected to screen space when a module submits them, then
    // drawn through the GUI canvas (DrawContext) during the 2D pass. This is the
    // SAME path the HUD uses, so it renders correctly whether or not Iris is
    // installed. The previous approach pushed RenderType.lines() into
    // mc.renderBuffers().bufferSource(), but Iris replaces that with a deferred
    // FullyBufferedMultiBufferSource, so those lines were flushed later in world
    // space (misaligned / invisible) — and crashed on the missing vertex normal.
    private record ScreenLine(float x1, float y1, float x2, float y2, int color) {}
    private static final List<ScreenLine> pendingLines = new ArrayList<>();
    private static final int MAX_PENDING_LINES = 200_000;

    // Minimum clip-space w (roughly: distance in front of the camera) a vertex may
    // have before the perspective divide blows its screen coordinates up to tens of
    // thousands of pixels. Edges crossing this plane are clipped to it instead.
    private static final float NEAR_W = 0.05f;
    private static final Vector4f clipA = new Vector4f();
    private static final Vector4f clipB = new Vector4f();

    // ========== Legacy GPU-side state (for withCameraModelView compat) ==========
    private static Camera camera;
    private static final Matrix4f viewMatrix = new Matrix4f();
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static PerspectiveProjectionMatrixBuffer projectionBuffer;

    /**
     * Snapshot the camera and the matrices the world is rendered with. Called at
     * the HEAD of {@code LevelRenderer.renderLevel}, before the frame-graph pass
     * can mutate the shared matrix instances. Copies the values into our own
     * matrices, so later mutation of the arguments cannot affect us.
     */
    public static void capture(Camera mainCamera, Matrix4f view, Matrix4f projection) {
        // New frame of overlay geometry starts here (before RenderEvent fires).
        pendingLines.clear();

        camera = mainCamera;

        Vec3 pos = mainCamera.getPosition();
        camX = (float) pos.x;
        camY = (float) pos.y;
        camZ = (float) pos.z;

        screenW = mc.getWindow().getGuiScaledWidth();
        screenH = mc.getWindow().getGuiScaledHeight();

        if (view != null) viewMatrix.set(view);
        if (projection != null) projectionMatrix.set(projection);

        viewProj.identity();
        viewProj.mul(projectionMatrix);
        viewProj.mul(viewMatrix);

        capturedThisFrame = true;
    }

    public static void begin(Camera mainCamera, Matrix4f view, Matrix4f projection) {
        active = true;

        // When an Iris shaderpack is active, the frustumMatrix/projectionMatrix
        // arguments no longer describe how the world was actually drawn (Iris
        // drives its own gbuffer matrices), so project against the exact matrices
        // the shader used. Read at renderLevel TAIL, where they still hold the
        // terrain-pass values (the hand is rendered afterwards).
        Matrix4f irisView = null;
        Matrix4f irisProj = null;
        if (IrisCompatibility.isShaderPackInUse()) {
            irisView = IrisCompatibility.getGbufferModelView();
            irisProj = IrisCompatibility.getGbufferProjection();
        }

        if (irisView != null && irisProj != null) {
            capture(mainCamera, irisView, irisProj);
        } else if (!capturedThisFrame) {
            // No shaderpack: fall back to the matrices snapshotted at renderLevel
            // HEAD (or, if that hook never ran, to these TAIL arguments).
            capture(mainCamera, view, projection);
        }
    }

    public static void end() {
        active = false;
        capturedThisFrame = false;
    }

    public static boolean isActive() { return active; }

    /**
     * Draw all queued world-overlay geometry through the GUI canvas. MUST be
     * called during the 2D pass, while a DrawContext canvas is active (see
     * GameRendererPatch#onRender). Clears the queue afterwards.
     */
    public static void flush(DrawContext canvas) {
        try {
            if (canvas != null && !pendingLines.isEmpty()) {
                for (ScreenLine line : pendingLines) {
                    canvas.drawLine(line.x1(), line.y1(), line.x2(), line.y2(),
                            new Paint().setColor(line.color()).setStrokeWidth(1.5f));
                }
            }
        } finally {
            pendingLines.clear();
        }
    }

    // ========== Camera-relative helpers ==========

    public static void toCameraRelative(double worldX, double worldY, double worldZ, Vector3f out) {
        out.set((float)(worldX - camX), (float)(worldY - camY), (float)(worldZ - camZ));
    }

    public static void toView(double worldX, double worldY, double worldZ, Vector3f out) {
        toCameraRelative(worldX, worldY, worldZ, out);
    }

    public static Camera camera() {
        return camera != null ? camera : mc.gameRenderer.getMainCamera();
    }

    // ========== GPU-side rendering (legacy compat for withCameraModelView) ==========

    public static void withIdentityModelView(Runnable draw) {
        withCameraModelView(draw);
    }

    public static void withCameraModelView(Runnable draw) {
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        // Stop Iris from hijacking these draws with its world shaders (no-op when
        // Iris is absent).
        IrisCompatibility.beginOverlayIsolation();
        try {
            if (projectionBuffer == null) {
                projectionBuffer = new PerspectiveProjectionMatrixBuffer("openzen-world-overlay");
            }
            RenderSystem.setProjectionMatrix(
                    projectionBuffer.getBuffer(new Matrix4f(projectionMatrix)), ProjectionType.PERSPECTIVE);
            stack.set(viewMatrix);
            RenderSystem.resetModelOffset();
            draw.run();
        } finally {
            IrisCompatibility.endOverlayIsolation();
            stack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    // ========== CPU-side screen-space projection ==========

    /**
     * Project a world position to GUI-scaled screen coordinates using the exact
     * matrices the world was rendered with this frame. Returns {@code false} (and
     * leaves {@code outXY} untouched) when the point is behind the camera.
     *
     * <p>This is the single source of truth for world-to-screen projection; both
     * the box overlays here and {@code ProjectionUtil} route through it so every
     * overlay lines up with the actual render, with or without Iris.
     */
    public static boolean projectToScreen(double wx, double wy, double wz, float[] outXY) {
        return project(wx, wy, wz, outXY);
    }

    private static boolean project(double wx, double wy, double wz, float[] outXY) {
        ndc.set((float)(wx - camX), (float)(wy - camY), (float)(wz - camZ), 1.0f);
        ndc.mul(viewProj);
        if (ndc.w <= NEAR_W) return false;
        toScreen(ndc, outXY);
        return true;
    }

    /**
     * Project one box edge, clipping it to the near plane rather than dropping it.
     *
     * <p>An edge with an endpoint at or behind the near plane has {@code w} near
     * zero, and the perspective divide would throw its screen position tens of
     * thousands of pixels away — drawn as a streak across the whole screen. Here the
     * endpoint is instead moved along the edge to where it meets the near plane, so
     * the visible part of the edge still renders in the right place.
     *
     * @return false when the whole edge is at or behind the near plane
     */
    private static boolean projectSegment(double ax, double ay, double az,
                                          double bx, double by, double bz,
                                          float[] outA, float[] outB) {
        toClip(ax, ay, az, clipA);
        toClip(bx, by, bz, clipB);

        boolean aBehind = clipA.w <= NEAR_W;
        boolean bBehind = clipB.w <= NEAR_W;
        if (aBehind && bBehind) {
            return false;
        }
        if (aBehind) {
            clipA.lerp(clipB, (NEAR_W - clipA.w) / (clipB.w - clipA.w));
        } else if (bBehind) {
            clipB.lerp(clipA, (NEAR_W - clipB.w) / (clipA.w - clipB.w));
        }

        toScreen(clipA, outA);
        toScreen(clipB, outB);
        return true;
    }

    private static void toClip(double wx, double wy, double wz, Vector4f out) {
        out.set((float)(wx - camX), (float)(wy - camY), (float)(wz - camZ), 1.0f);
        out.mul(viewProj);
    }

    private static void toScreen(Vector4f clip, float[] outXY) {
        float invW = 1.0f / clip.w;
        outXY[0] = (clip.x * invW * 0.5f + 0.5f) * screenW;
        outXY[1] = (1.0f - (clip.y * invW * 0.5f + 0.5f)) * screenH;
    }

    public static void drawSolidBox(AABB box, PoseStack poseStack,
                                     float red, float green, float blue, float alpha) {
        drawOutlineBox(box, poseStack, red, green, blue, alpha);
    }

    public static void drawOutlineBox(AABB box, PoseStack poseStack,
                                       float red, float green, float blue, float alpha) {
        drawOutlineBox(box, poseStack, red, green, blue, alpha, red, green, blue);
    }

    public static void drawOutlineBox(AABB box, PoseStack poseStack,
                                       float red, float green, float blue, float alpha,
                                       float red2, float green2, float blue2) {
        double[][][] edges = {
                {{box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ}},
                {{box.maxX, box.minY, box.minZ}, {box.maxX, box.minY, box.maxZ}},
                {{box.maxX, box.minY, box.maxZ}, {box.minX, box.minY, box.maxZ}},
                {{box.minX, box.minY, box.maxZ}, {box.minX, box.minY, box.minZ}},
                {{box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ}},
                {{box.maxX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.maxZ}},
                {{box.maxX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.maxZ}},
                {{box.minX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.minZ}},
                {{box.minX, box.minY, box.minZ}, {box.minX, box.maxY, box.minZ}},
                {{box.maxX, box.minY, box.minZ}, {box.maxX, box.maxY, box.minZ}},
                {{box.maxX, box.minY, box.maxZ}, {box.maxX, box.maxY, box.maxZ}},
                {{box.minX, box.minY, box.maxZ}, {box.minX, box.maxY, box.maxZ}},
        };

        if (pendingLines.size() >= MAX_PENDING_LINES) {
            return;
        }

        float[] p1 = new float[2], p2 = new float[2];
        for (int i = 0; i < edges.length; i++) {
            double[][] e = edges[i];
            if (!projectSegment(e[0][0], e[0][1], e[0][2], e[1][0], e[1][1], e[1][2], p1, p2)) continue;

            float r = (i < 4) ? red : (i < 8) ? red2 : red;
            float g = (i < 4) ? green : (i < 8) ? green2 : green;
            float b = (i < 4) ? blue : (i < 8) ? blue2 : blue;

            pendingLines.add(new ScreenLine(p1[0], p1[1], p2[0], p2[1], toArgb(r, g, b, alpha)));
        }
    }

    private static int toArgb(float r, float g, float b, float a) {
        return clamp255(a) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
    }

    private static int clamp255(float v) {
        int i = Math.round(v * 255.0f);
        return i < 0 ? 0 : Math.min(i, 255);
    }

    private WorldOverlayRenderer() {}
}

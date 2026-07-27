package shit.zen.utils.render;

import lombok.Generated;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import shit.zen.ClientBase;
import shit.zen.utils.math.Vector2f;

public final class ProjectionUtil
extends ClientBase {
    public static void updateMatrices() {
        // Projection uses the exact per-frame view/projection matrices captured by
        // WorldOverlayRenderer (from LevelRenderer.renderLevel); nothing to do here.
    }

    public static Vector2f project(double worldX, double worldY, double worldZ, float partialTicks) {
        // Delegate to the matrix-based projection so overlays match what the game
        // actually renders. The previous hand-rolled projection used the base FOV
        // option (mc.options.fov()) and a manual camera rotation, which ignores the
        // real render FOV / view transforms and drifts out of alignment — most
        // visibly once Iris/Sodium is installed.
        float[] out = new float[2];
        if (!WorldOverlayRenderer.projectToScreen(worldX, worldY, worldZ, out)) {
            return null;
        }
        return new Vector2f(out[0], out[1]);
    }

    public static Vector2f project(double worldX, double worldY, double worldZ) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return ProjectionUtil.project(worldX, worldY, worldZ, partialTick);
    }

    @Generated
    private ProjectionUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

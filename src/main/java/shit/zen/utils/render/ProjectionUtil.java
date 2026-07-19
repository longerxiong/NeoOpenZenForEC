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
        // Projection is calculated from the current camera and FOV in project().
    }

    public static Vector2f project(double worldX, double worldY, double worldZ, float partialTicks) {
        Vec3 cameraPos = mc.getEntityRenderDispatcher().camera.getPosition();
        Quaternionf cameraRotation = new Quaternionf(mc.getEntityRenderDispatcher().cameraOrientation());
        cameraRotation.conjugate();
        Vector3f relativePos = new Vector3f((float)(cameraPos.x - worldX), (float)(cameraPos.y - worldY), (float)(cameraPos.z - worldZ));
        relativePos.rotate(cameraRotation);
        double fov = mc.options.fov().get();
        return ProjectionUtil.projectInternal(relativePos, fov);
    }

    private static Vector2f projectInternal(Vector3f relativePos, double fov) {
        if (relativePos.z() >= -1.0E-4f) {
            return null;
        }
        float halfHeight = (float)mc.getWindow().getGuiScaledHeight() / 2.0f;
        float scale = halfHeight / (relativePos.z() * (float)Math.tan(Math.toRadians(fov / 2.0)));
        return new Vector2f(-relativePos.x() * scale + (float)mc.getWindow().getGuiScaledWidth() / 2.0f, (float)mc.getWindow().getGuiScaledHeight() / 2.0f - relativePos.y() * scale);
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

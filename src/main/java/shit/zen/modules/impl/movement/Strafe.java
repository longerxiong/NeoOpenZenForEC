package shit.zen.modules.impl.movement;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec2;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MoveEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.MovementUtil;

/**
 * Redirects existing horizontal velocity toward the current movement input.
 * The velocity magnitude is preserved so speed modules can remain in control
 * of acceleration and maximum speed.
 */
public class Strafe extends Module {
    public static Strafe INSTANCE;

    public final NumberSetting strengthInAir =
            new NumberSetting("Strength In Air", 1.0, 0.0, 1.0, 0.05);
    public final NumberSetting strengthOnGround =
            new NumberSetting("Strength On Ground", 1.0, 0.0, 1.0, 0.05);
    public final BooleanSetting strictMovement = new BooleanSetting("Strict Movement", false);

    public Strafe() {
        super("Strafe", Category.MOVEMENT);
        INSTANCE = this;
    }

    @EventTarget(EventPriority.LOWEST)
    public void onMove(MoveEvent event) {
        if (event.getType() != MoverType.SELF || mc.player == null || mc.level == null) {
            return;
        }
        if (TargetStrafe.isActive()) {
            return;
        }

        double strength = (mc.player.onGround()
                ? this.strengthOnGround.getValue()
                : this.strengthInAir.getValue()).doubleValue();
        if (strength <= 0.0) {
            return;
        }

        Vec2 input = mc.player.input.getMoveVector();
        double forward = input.y;
        double side = input.x;
        if (Math.abs(forward) < 1.0E-4 && Math.abs(side) < 1.0E-4) {
            if (this.strictMovement.getValue()) {
                event.setX(0.0);
                event.setZ(0.0);
            }
            return;
        }

        double speed = Math.hypot(event.getX(), event.getZ());
        if (speed < 1.0E-7) {
            return;
        }

        double currentDirection = Math.atan2(-event.getX(), event.getZ());
        double targetDirection = MovementUtil.getDirectionYaw(mc.player.getYRot(), forward, side);
        double angleDelta = Math.atan2(
                Math.sin(targetDirection - currentDirection),
                Math.cos(targetDirection - currentDirection));
        double direction = currentDirection + angleDelta * strength;
        event.setX(-Math.sin(direction) * speed);
        event.setZ(Math.cos(direction) * speed);
    }
}

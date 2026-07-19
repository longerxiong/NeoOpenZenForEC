package shit.zen.modules.impl.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.game.MovementUtil;

/**
 * Speed module with multiple modes for different anti-cheat bypasses.
 */
public class Speed extends Module {
    public static Speed INSTANCE;

    // === Mode ===
    public final ModeSetting mode = new ModeSetting("Mode", "Vanilla", "Collide").withDefault("Vanilla");

    // === Vanilla settings ===
    public final NumberSetting vanillaSpeed = new NumberSetting("Speed", 1, 1, 5, 0.1f,
            () -> this.mode.is("Vanilla"));

    // === Grim collide settings ===
    public final NumberSetting boostSpeed = new NumberSetting("BoostSpeed", 0.08f, 0.01f, 0.08f, 0.01f,
            () -> this.mode.is("Collide"));
    public final NumberSetting shrinkBox = new NumberSetting("ShrinkBox", 0.5f, 0.1f, 2.0f, 0.1f,
            () -> this.mode.is("Collide"));

    public Speed() {
        super("Speed", Category.MOVEMENT);
        INSTANCE = this;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        if (mc.player == null || mc.level == null) return;

        switch (this.mode.getValue()) {
            case "Vanilla" -> handleVanilla();
            case "Collide" -> handleGrimCollide();
        }
    }

    private void handleVanilla() {
        if (MovementUtil.isMoving() && mc.player.onGround()) {
            MovementUtil.strafeForward(vanillaSpeed.getValue().doubleValue() / 12);
        }
    }

    private void handleGrimCollide() {
        if (!MovementUtil.isInputActive()) {
            return;
        }

        AABB box = mc.player.getBoundingBox().inflate(shrinkBox.getValue().doubleValue());
        long collisions = mc.level.getEntities(mc.player, box, entity ->
                        entity instanceof LivingEntity && !(entity instanceof ArmorStand))
                .stream()
                .filter(entity -> box.intersects(entity.getBoundingBox()))
                .count();

        double yaw = Math.toRadians(mc.player.getYRot());
        double boost = boostSpeed.getValue().doubleValue() * collisions;
        mc.player.push(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
    }
}

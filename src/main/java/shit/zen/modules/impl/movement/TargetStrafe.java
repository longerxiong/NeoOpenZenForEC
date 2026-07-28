package shit.zen.modules.impl.movement;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.ZenClient;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.MoveEvent;
import shit.zen.event.impl.SneakEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.MovementUtil;
import shit.zen.event.EventTarget;

public class TargetStrafe
extends Module {
    public static TargetStrafe INSTANCE;
    private final Timer collisionTimer = new Timer();
    private final BooleanSetting smartStrafe = new BooleanSetting("Jump Key Only", true);
    private final ModeSetting mode = new ModeSetting("Mode", "Grim", "Adaptive").withDefault("Grim");
    private final NumberSetting range = new NumberSetting("Range", 0.5f, 0.1f, 2.0f, 0.1f, ()->mode.is("Grim"));
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 1000, 100, 5000, 100,()->mode.is("Grim"));
    private final NumberSetting distance = new NumberSetting("Distance", 2, .5, 4.5, .1,()->mode.is("Adaptive"));
    private final NumberSetting points = new NumberSetting("Points", 12, 3, 16, 1,()->mode.is("Adaptive"));
    public static int strafeDirectionSign;
    public static Entity strafeTarget;
    private final Timer switchTimer = new Timer();
    private int direction = 1;

    public TargetStrafe() {
        super("TargetStrafe", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static float getRange() {
        return TargetStrafe.INSTANCE.range.getValue().floatValue();
    }

    public static boolean isSmartStrafe() {
        return TargetStrafe.INSTANCE.smartStrafe.getValue();
    }

    public static boolean isActive() {
        return INSTANCE != null
                && INSTANCE.isEnabled()
                && strafeTarget != null
                && (!isSmartStrafe() || mc.options.keyJump.isDown());
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mode.is("Grim")) {
            if (motionEvent.isPost() && mc.player != null) {
                boolean aboveVoid;
                AABB playerBox;
                if (KillAura.target == null) {
                    strafeTarget = null;
                } else if (this.switchTimer.hasPassed(this.switchDelay.getValue().intValue()) || strafeTarget == null) {
                    ArrayList<Entity> sortedTargets = new ArrayList<>(KillAura.targetList);
                    sortedTargets.sort((a, b) -> {
                        float distA = mc.player.distanceTo(a);
                        float distB = mc.player.distanceTo(b);
                        return Float.compare(distA, distB);
                    });
                    if (!sortedTargets.isEmpty()) {
                        strafeTarget = sortedTargets.get(0);
                        this.switchTimer.reset();
                    }
                }
                playerBox = mc.player.getBoundingBox();
                aboveVoid = MovementUtil.isAboveVoid(playerBox.minX, playerBox.minY, playerBox.minZ) || MovementUtil.isAboveVoid(playerBox.minX, playerBox.minY, playerBox.maxZ) || MovementUtil.isAboveVoid(playerBox.maxX, playerBox.minY, playerBox.minZ) || MovementUtil.isAboveVoid(playerBox.maxX, playerBox.minY, playerBox.maxZ);
                if ((aboveVoid || mc.player.horizontalCollision) && this.collisionTimer.hasPassedDouble(500.0, true)) {
                    strafeDirectionSign *= -1;
                }
            }
        }
    }

    @EventTarget
    public void onSneak(SneakEvent sneakEvent) {
        if(mode.is("Grim")) {
            if (!sneakEvent.isCancelled() && !FireballBlink.INSTANCE.isEnabled()) {
                sneakEvent.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onMove(MoveEvent event){
        if (mc.player == null || mc.options == null || check()) return;
        if(!mode.is("Adaptive"))return;


        strafeTarget = getTarget();
        if (strafeTarget == null) {
            return;
        }

        if (mc.options.keyLeft.isDown()) {
            direction = 1;
        } else if (mc.options.keyRight.isDown()) {
            direction = -1;
        }

        if (mc.player.horizontalCollision) {
            direction = -direction;
        }

        Vec3 goal = getGoal(strafeTarget);
        if (goal == null) return;

        double diffX = goal.x - mc.player.getX();
        double diffZ = goal.z - mc.player.getZ();

        double speed = MovementUtil.getSpeed();
        double yaw = Math.atan2(diffZ, diffX);
        double motionX = speed * Math.cos(yaw);
        double motionZ = speed * Math.sin(yaw);

        if (isOverVoid(mc.player.getX() + motionX, mc.player.getY(), mc.player.getZ() + motionZ)) {
            direction = -direction;
            return;
        }

        event.setX(motionX);
        event.setZ(motionZ);
    }

    private Vec3 getGoal(Entity target) {
        if (mc.player == null || target == null) return null;
        double dist = Math.max(.1, distance.getValue().doubleValue());

        if (mode.is("Behind")) {
            double yaw = Math.toRadians(target.getYRot() + 180);
            return new Vec3(target.getX() - Math.sin(yaw) * dist, target.getY(), target.getZ() + Math.cos(yaw) * dist);
        }

        double currentAngle = Math.atan2(mc.player.getZ() - target.getZ(), mc.player.getX() - target.getX());
        double angleStep = (Math.PI * 2.0) / points.getValue().intValue();
        double nextAngle = currentAngle + (direction * angleStep);
        return new Vec3(target.getX() + Math.cos(nextAngle) * dist, target.getY(), target.getZ() + Math.sin(nextAngle) * dist);
    }

    public Entity getTarget() {
        KillAura aura = ZenClient.getInstance().getModuleManager().getModule(KillAura.class);
        return aura.isEnabled() ? aura.getTarget() : null;
    }

    private boolean check() {
        if (mc.player == null) return true;
//        setSuffix(mode.getValue());
        if (ZenClient.getInstance().getModuleManager().getModule(Scaffold.class).isEnabled()) return true;
        return smartStrafe.getValue() && !mc.options.keyJump.isDown();
    }

    public boolean isOverVoid(double x, double y, double z) {
        if (mc.player == null || mc.level == null) return false;

        ClipContext context = new ClipContext(
                new Vec3(x, y, z),
                new Vec3(x, mc.level.getMinY(), z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        );

        BlockPos hitPos = mc.level.clip(context).getBlockPos();
        return mc.level.getBlockState(hitPos).is(Blocks.AIR) || hitPos.getY() <= mc.level.getMinY();
    }


    static {
        strafeDirectionSign = 1;
    }
}

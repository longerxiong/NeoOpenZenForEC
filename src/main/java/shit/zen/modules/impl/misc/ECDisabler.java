package shit.zen.modules.impl.exploit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.misc.PacketUtil;

import java.util.Random;

public class ECDisabler extends Module {
    public static ECDisabler INSTANCE;

    private final Random EC = new Random();
    private int EC_TICK = 0;
    private double EC_X, EC_Y, EC_Z;
    private boolean EC_FLAG1, EC_FLAG2;

    public final ModeSetting EC_MODE = new ModeSetting("Mode", "FUCKEC1",
            "FUCKEC1", "FUCKEC2", "FUCKEC3", "FUCKEC4", "FUCKEC5",
            "FUCKEC6", "FUCKEC7", "FUCKEC8", "FUCKEC9", "FUCKEC10"
    );

    public ECDisabler() {
        super("ECDisabler", Category.EXPLOIT);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            EC_X = mc.player.getX();
            EC_Y = mc.player.getY();
            EC_Z = mc.player.getZ();
        }
        EC_TICK = 0;
        EC_FLAG1 = false;
        EC_FLAG2 = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.player == null) return;
        EC_TICK++;

        String EC_M = this.EC_MODE.getValue();

        if (EC_M.equals("FUCKEC1")) {
            if (EC.nextBoolean()) {
                EC_X += (EC.nextDouble() - 0.5) * 0.001;
            }
            if (EC_TICK % 3 == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(180 + EC.nextFloat() * 10, 45 + EC.nextFloat() * 10, mc.player.onGround(), mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC2")) {
            if (EC_TICK % 2 == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(EC_X, EC_Y, EC_Z, false, mc.player.horizontalCollision));
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(EC_X, EC_Y, EC_Z, true, mc.player.horizontalCollision));
            }
            EC_Y = mc.player.getY();
        } else if (EC_M.equals("FUCKEC3")) {
            if (EC.nextFloat() < 0.1) {
                double EC_DX = (EC.nextDouble() - 0.5) * 0.02;
                double EC_DZ = (EC.nextDouble() - 0.5) * 0.02;
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + EC_DX, mc.player.getY(), mc.player.getZ() + EC_DZ, false, mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC4")) {
            if (EC_TICK % 5 == 0) {
                BlockPos EC_FAR = new BlockPos(2000000 + EC.nextInt(1000000), 64, 2000000 + EC.nextInt(1000000));
                BlockHitResult EC_HIT = new BlockHitResult(new Vec3(EC_FAR.getX(), EC_FAR.getY(), EC_FAR.getZ()), Direction.UP, EC_FAR, false);
                PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT, 0));
            }
        } else if (EC_M.equals("FUCKEC5")) {
            if (EC.nextBoolean()) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(0, 90, mc.player.onGround(), mc.player.horizontalCollision));
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 0.01, mc.player.getY() - 0.01, mc.player.getZ() + 0.01, true, mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC6")) {
            if (EC_TICK % 4 == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                PacketUtil.send(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC7")) {
            for (int EC_I = 0; EC_I < 3; EC_I++) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(EC.nextFloat() * 360, EC.nextFloat() * 90, mc.player.onGround(), mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC8")) {
            if (EC_TICK % 3 == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 0.05, mc.player.getZ(), 45, 45, false, mc.player.horizontalCollision));
                BlockPos EC_FAR2 = new BlockPos(3000000, 64, 3000000);
                BlockHitResult EC_HIT2 = new BlockHitResult(new Vec3(3000000, 64, 3000000), Direction.UP, EC_FAR2, false);
                PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT2, 0));
            }
        } else if (EC_M.equals("FUCKEC9")) {
            double EC_TMP = 0;
            for (int EC_I = 0; EC_I < 10; EC_I++) EC_TMP += EC.nextDouble();
            if (EC_TMP > 5) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(180, 0, mc.player.onGround(), mc.player.horizontalCollision));
            }
        } else if (EC_M.equals("FUCKEC10")) {
            int EC_CHOICE = EC.nextInt(3);
            if (EC_CHOICE == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 0.001, mc.player.getY(), mc.player.getZ() - 0.001, true, mc.player.horizontalCollision));
            } else if (EC_CHOICE == 1) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(90 + EC.nextFloat() * 20, 30 + EC.nextFloat() * 20, mc.player.onGround(), mc.player.horizontalCollision));
            } else {
                BlockPos EC_FAR3 = new BlockPos(4000000, 128, 4000000);
                BlockHitResult EC_HIT3 = new BlockHitResult(new Vec3(4000000, 128, 4000000), Direction.UP, EC_FAR3, false);
                PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT3, 0));
            }
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (!this.isEnabled() || mc.player == null) return;

        String EC_M = this.EC_MODE.getValue();

        if (EC_M.equals("FUCKEC1")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(Float.MAX_VALUE, Float.MAX_VALUE, mc.player.onGround(), mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC2")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 0.0001, mc.player.getY(), mc.player.getZ() - 0.0001, true, mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC3")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 100, mc.player.getZ(), false, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC4")) {
            BlockPos EC_FAR = new BlockPos(5000000, 64, 5000000);
            BlockHitResult EC_HIT = new BlockHitResult(new Vec3(5000000, 64, 5000000), Direction.UP, EC_FAR, false);
            PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT, 0));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC5")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(90, 45, mc.player.onGround(), mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC6")) {
            double EC_DX = EC.nextDouble() * 0.01;
            double EC_DZ = EC.nextDouble() * 0.01;
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + EC_DX, mc.player.getY(), mc.player.getZ() + EC_DZ, false, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(180, 0, mc.player.onGround(), mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC7")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.42, mc.player.getZ(), false, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC8")) {
            for (int EC_I = 0; EC_I < 5; EC_I++) {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(EC.nextFloat() * 360, EC.nextFloat() * 90, mc.player.onGround(), mc.player.horizontalCollision));
            }
            BlockPos EC_FAR2 = new BlockPos(6000000, 64, 6000000);
            BlockHitResult EC_HIT2 = new BlockHitResult(new Vec3(6000000, 64, 6000000), Direction.UP, EC_FAR2, false);
            PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT2, 0));
        } else if (EC_M.equals("FUCKEC9")) {
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(45, 45, mc.player.onGround(), mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 0.02, mc.player.getY() - 0.02, mc.player.getZ() + 0.02, true, mc.player.horizontalCollision));
            PacketUtil.send(new ServerboundMovePlayerPacket.Rot(180, 0, mc.player.onGround(), mc.player.horizontalCollision));
        } else if (EC_M.equals("FUCKEC10")) {
            int EC_RND = EC.nextInt(3);
            if (EC_RND == 0) {
                PacketUtil.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), 0, 90, false, mc.player.horizontalCollision));
            } else if (EC_RND == 1) {
                BlockPos EC_FAR3 = new BlockPos(7000000, 64, 7000000);
                BlockHitResult EC_HIT3 = new BlockHitResult(new Vec3(7000000, 64, 7000000), Direction.UP, EC_FAR3, false);
                PacketUtil.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, EC_HIT3, 0));
            } else {
                PacketUtil.send(new ServerboundMovePlayerPacket.Rot(90, 90, mc.player.onGround(), mc.player.horizontalCollision));
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.player == null || event.isIncoming()) return;

        String EC_M = this.EC_MODE.getValue();
        if (EC_M.equals("FUCKEC1")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.Rot && EC.nextBoolean()) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC2")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.Pos && EC_TICK % 2 == 0) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC3")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket && mc.player.onGround() && EC.nextFloat() < 0.3) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC4")) {
            if (event.getPacket() instanceof ServerboundUseItemOnPacket && EC.nextBoolean()) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC5")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.StatusOnly && EC_TICK % 3 == 0) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC6")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket && EC.nextDouble() > 0.7) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC7")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.Pos && mc.player.fallDistance > 0.1) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC8")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.Rot && EC_TICK % 4 == 0) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC9")) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket && EC.nextBoolean()) {
                event.setCancelled(true);
            }
        } else if (EC_M.equals("FUCKEC10")) {
            // Bypass
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || mc.player == null) return;

        String EC_M = this.EC_MODE.getValue();
        if (EC_M.equals("FUCKEC1")) {
            double EC_FACTOR = 0.8 + EC.nextDouble() * 0.4;
            MovementUtil.strafeForward(MovementUtil.getSpeed() * EC_FACTOR);
        } else if (EC_M.equals("FUCKEC2")) {
            if (EC.nextBoolean()) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 1.1);
            }
        } else if (EC_M.equals("FUCKEC3")) {
            if (mc.player.onGround() && EC.nextFloat() < 0.2) {
                MovementUtil.strafeForward(0.0);
            }
        } else if (EC_M.equals("FUCKEC4")) {
           //FUCK EC4
        } else if (EC_M.equals("FUCKEC5")) {
            if (EC_TICK % 2 == 0) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 1.05);
            }
        } else if (EC_M.equals("FUCKEC6")) {
            if (EC.nextBoolean()) {
                MovementUtil.strafeForward(-MovementUtil.getSpeed());
            }
        } else if (EC_M.equals("FUCKEC7")) {
            if (mc.player.isInWater()) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 0.5);
            }
        } else if (EC_M.equals("FUCKEC8")) {
            if (EC.nextFloat() < 0.1) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 0.8);
            }
        } else if (EC_M.equals("FUCKEC9")) {
            if (EC.nextBoolean()) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 0.9);
            }
        } else if (EC_M.equals("FUCKEC10")) {
            if (EC.nextBoolean()) {
                MovementUtil.strafeForward(MovementUtil.getSpeed() * 1.2);
            }
        }
    }

    @EventTarget
    public void onReceivePacket(PacketEvent event) {
        if (!this.isEnabled() || mc.player == null || !event.isIncoming()) return;
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
           
        }
    }
}

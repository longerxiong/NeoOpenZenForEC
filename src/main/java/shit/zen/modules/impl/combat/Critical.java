package shit.zen.modules.impl.combat;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.event.impl.EntityRemoveEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.utils.misc.PacketUtil;

public class Critical
extends Module {
    public static Critical INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode", "Heypixel", "Packet").withDefault("Heypixel");
    public Critical() {
        super("Critical", Category.COMBAT);
        INSTANCE = this;
    }

    @EventTarget
    public void onEntityRemove(EntityRemoveEvent entityRemoveEvent) {
        if (mc.player == null) {
            return;
        }
        switch(this.mode.getValue()) {
            case "Heypixel" -> {
                boolean canCrit = mc.player.fallDistance > 0.0f && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater() && !mc.player.hasEffect(MobEffects.BLINDNESS) && !mc.player.isPassenger() && entityRemoveEvent.entity() instanceof LivingEntity;
                boolean wasSprinting = mc.player.isSprinting();
                if (canCrit && !entityRemoveEvent.dead()) {
                    mc.player.resetAttackStrengthTicker();
                }
                if (canCrit && wasSprinting && entityRemoveEvent.dead()) {
                    mc.options.keySprint.setDown(false);
                }
            }

            case "Packet" -> {
                double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
                boolean h = mc.player.horizontalCollision;
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, h));
                PacketUtil.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, h));
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent){
        if(this.mode.is("Packet"))motionEvent.setOnGround(false);
    }
}
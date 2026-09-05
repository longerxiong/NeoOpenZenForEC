package shit.zen.modules.impl.movement;

import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.misc.PacketUtil;

public class ECFly extends Module {

    public final NumberSetting flySpeed = new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1);
    public final NumberSetting fuckEC = new NumberSetting("FUCK EC", 200.0, 10.0, 5000.0, 50.0);

    public ECFly() {
        super("ECFly", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        mc.player.getAbilities().flying = true;
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        
        if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            mc.player.getAbilities().flying = false;
        }

        Abilities abilities = new Abilities();
        abilities.flying = false;
        abilities.mayfly = false;
        PacketUtil.sendQueued(new ServerboundPlayerAbilitiesPacket(abilities));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        Abilities creativeAbilities = new Abilities();
        creativeAbilities.flying = true;
        creativeAbilities.mayfly = true;
        creativeAbilities.instabuild = true;
        creativeAbilities.invulnerable = true;

        Abilities opAbilities = new Abilities();
        opAbilities.flying = true;
        opAbilities.mayfly = true;
        opAbilities.instabuild = true;
        opAbilities.invulnerable = true;
        opAbilities.setWalkingSpeed(0.1f);
        opAbilities.setFlyingSpeed(0.05f);

        ServerboundPlayerAbilitiesPacket creativePacket = new ServerboundPlayerAbilitiesPacket(creativeAbilities);
        ServerboundPlayerAbilitiesPacket opPacket = new ServerboundPlayerAbilitiesPacket(opAbilities);

        int burstCount = this.fuckEC.getValue().intValue();
        for (int i = 0; i < burstCount; i++) {
            PacketUtil.sendQueued(creativePacket);
            PacketUtil.sendQueued(opPacket);
        }

        mc.player.getAbilities().flying = true;
        
        Vec3 motion = mc.player.getDeltaMovement();
        double yMotion = 0.0;

        if (mc.options.keyJump.isDown()) {
            yMotion = this.flySpeed.getValue().doubleValue();
        } else if (mc.options.keyShift.isDown()) {
            yMotion = -this.flySpeed.getValue().doubleValue();
        }

        mc.player.setDeltaMovement(motion.x, yMotion, motion.z);
    }
}

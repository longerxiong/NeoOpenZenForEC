package shit.zen.modules.impl.combat.antikb;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import shit.zen.event.impl.*;

public class VanillaMode extends AntiKBMode{
    public static VanillaMode INSTANCE;

    public VanillaMode() {
        super("Vanilla");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public void onRotation(RotationEvent var1) {
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent var1) {
        if (var1.getPacket() instanceof ClientboundSetEntityMotionPacket vel && vel.getId() == mc.player.getId()) {
            var1.setCancelled(true);
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent var1) {
    }

    @Override
    public void onPreMotion(PreMotionEvent var1) {
    }

    @Override
    public void onGameTick(GameTickEvent var1) {
    }

    @Override
    public void onSprint(SprintEvent var1) {
    }

    @Override
    public void onTick(TickEvent var1) {
    }

    @Override
    public void onStrafe(StrafeEvent var1) {
    }

    @Override
    public void onMotion(MotionEvent var1) {
    }
}

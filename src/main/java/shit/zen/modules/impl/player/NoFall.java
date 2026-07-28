package shit.zen.modules.impl.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import shit.zen.event.impl.KeyEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.event.EventTarget;

public class NoFall
extends Module {
    public static NoFall INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode",  "Vanilla", "Grim").withDefault("Vanilla");
    private final NumberSetting fallDistanceSetting = new NumberSetting("Fall Distance", 3.0, 0.0, 10.0, 0.5);
    private boolean isFalling = false;
    private boolean sentFlyPacket = false;
    public boolean jumpLandingBoost = false;
    private boolean receivedPositionPacket = false;
    public boolean boostActive = false;
    private int boostTick = 0;
    private int airTicks = 0;
    private boolean jumpToggle = false;

    public NoFall() {
        super("NoFall", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
    }

    private void reset() {
        this.isFalling = false;
        this.sentFlyPacket = false;
        this.jumpLandingBoost = false;
        this.receivedPositionPacket = false;
        this.boostActive = false;
        this.jumpToggle = false;
        this.boostTick = 0;
    }

    @EventTarget(value=0)
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.mode.is("Grim")) {
            this.airTicks = mc.player.onGround() ? 0 : ++this.airTicks;
            if (this.isFalling && this.airTicks > 0) {
                this.jumpToggle = !this.jumpToggle;
                mc.options.keyJump.setDown(this.jumpToggle);
            }
            if (this.receivedPositionPacket) {
                if (mc.player.getY() > 0.3525 && mc.options.keyJump.isDown() && this.airTicks > 0) {
                    this.boostActive = true;
                }
                if (this.boostActive) {
                    ++this.boostTick;
                    if (this.boostTick >= 21) {
                        this.reset();
                    }
                }
            }
        }
    }

    @EventTarget(value=0)
    public void onKey(KeyEvent keyEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.mode.is("Grim")) {
            if (this.isFalling && !mc.player.onGround() && keyEvent.getKeyCode() == 32) {
                keyEvent.setCancelled(true);
            }
            if (this.boostTick > 0 && keyEvent.getKeyCode() == 32) {
                keyEvent.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.mode.is("Grim")) {
            if (motionEvent.isPre() || mc.isSingleplayer()) {
                return;
            }
            if (mc.player.fallDistance >= this.fallDistanceSetting.getValue().floatValue()) {
                this.isFalling = true;
            }
            if (mc.player.onGround() && mc.player.verticalCollision && this.isFalling) {
                this.jumpLandingBoost = true;
                this.sentFlyPacket = true;
                this.sendFlyPacket();
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null) {
            return;
        }
        if(this.mode.is("Grim")){
            if (this.jumpLandingBoost) {
                mc.options.keyJump.setDown(true);
            } else if (!this.isFalling || mc.player.onGround()) {
                mc.options.keyJump.setDown(InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue()));
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        if(this.mode.is("Grim")){
            if (packetEvent.getPacket() instanceof ClientboundPlayerPositionPacket && this.sentFlyPacket) {
                this.receivedPositionPacket = true;
            }
        }
        if(this.mode.is("Vanilla")){
            if(packetEvent.getPacket() instanceof ServerboundMovePlayerPacket c03){
                if(mc.player.fallDistance >= this.fallDistanceSetting.getValue().floatValue()){
                    double x = c03.getX(mc.player.getX());
                    double y = c03.getY(mc.player.getY());
                    double z = c03.getZ(mc.player.getZ());
                    float yaw = c03.getYRot(mc.player.getYRot());
                    float pitch = c03.getXRot(mc.player.getXRot());
                    boolean onGround = true;
                    boolean horizontalCollision = c03.horizontalCollision();

                    packetEvent.setCancelled(true);

                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround, horizontalCollision));
                }
            }
        }
    }

    private void sendFlyPacket() {
        if (mc.player == null) {
            return;
        }
        PacketUtil.sendQueued(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }
}
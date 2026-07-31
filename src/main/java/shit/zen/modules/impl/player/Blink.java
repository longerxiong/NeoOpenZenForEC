package shit.zen.modules.impl.player;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.AntiBots;
import shit.zen.modules.impl.world.Teams;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.render.RenderUtil;

public class Blink extends Module {
    private static final int FAKE_PLAYER_ID = -1337;
    private static final int MAX_QUEUED_PACKETS = 8192;
    private static final int MAIN_COLOR = new Color(150, 45, 45, 255).getRGB();
    private static final Set<Class<?>> WHITELIST = Set.of(
            ClientIntentionPacket.class,
            ServerboundStatusRequestPacket.class,
            ServerboundPingRequestPacket.class,
            ServerboundHelloPacket.class,
            ServerboundKeyPacket.class
    );

    private final NumberSetting releaseOnDamage = new NumberSetting("Release Ticks On Damage", 20, 0, 50, 1);
    private final NumberSetting releaseSpeed = new NumberSetting("Release Speed", 10, 3, 20, 1);
    private final NumberSetting maxTicks = new NumberSetting("Max Ticks", 200, 10, 500, 1);
    private final NumberSetting playerDistance = new NumberSetting("Player Distance", 4.0, 0.0, 10.0, 0.1);
    private final NumberSetting tntDistance = new NumberSetting("TNT Distance", 5.0, 0.0, 10.0, 0.1);
    private final NumberSetting projectileExpand = new NumberSetting("Fake Player HitBox", 0.2, 0.0, 3.0, 0.01);

    private final Queue<Packet<?>> packets = new ArrayBlockingQueue<>(MAX_QUEUED_PACKETS);
    private final AtomicInteger blinkTicks = new AtomicInteger();
    private final SmoothAnimationTimer progress = new SmoothAnimationTimer();

    private BlinkGhostPlayer fakePlayer;
    private boolean draining;
    private int damageReleaseTicks;
    private int previousHurtTime;

    public Blink() {
        super("Blink", Category.MOVEMENT);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) {
            return;
        }
        if (!enabled && draining) {
            return;
        }
        if (!enabled && canDrain()) {
            draining = true;
            return;
        }
        super.setEnabled(enabled);
    }

    @Override
    protected void onEnable() {
        clearQueue();
        draining = false;
        damageReleaseTicks = 0;
        previousHurtTime = 0;
        progress.setCurrentValue(0.0);
        progress.animate(0.0, 0.2);

        if (mc.player == null || mc.level == null) {
            super.setEnabled(false);
            return;
        }
        createFakePlayer();
    }

    @Override
    protected void onDisable() {
        clearQueue();
        removeFakePlayer();
        draining = false;
        damageReleaseTicks = 0;
        previousHurtTime = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            disableImmediately();
            return;
        }

        int released = 0;
        int releaseLimit = releaseSpeed.getValue().intValue();
        int hurtTime = mc.player.hurtTime;
        if (hurtTime > 0 && previousHurtTime == 0) {
            damageReleaseTicks += releaseOnDamage.getValue().intValue();
        }
        previousHurtTime = hurtTime;

        while (released < releaseLimit && damageReleaseTicks > 0 && !packets.isEmpty()) {
            if (releaseTick()) {
                released++;
                damageReleaseTicks--;
            }
        }

        while (released < releaseLimit && !packets.isEmpty()
                && (draining
                || blinkTicks.get() >= maxTicks.getValue().intValue()
                || isPlayerInDanger())) {
            if (releaseTick()) {
                released++;
            }
        }

        updateProgress();
        if (draining && packets.isEmpty()) {
            super.setEnabled(false);
        }
    }

    @EventTarget(value = 4)
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (event.isIncoming()) {
            if (packet instanceof ClientboundPlayerPositionPacket) {
                clearQueue();
                mc.execute(this::disableImmediately);
            }
            return;
        }
        if (packet == null || event.isCancelled() || WHITELIST.contains(packet.getClass())) {
            return;
        }

        event.setCancelled(true);
        if (!packets.offer(packet)) {
            // Preserve the client under pathological packet spam. The queued prefix is
            // still replayed in order; only overflow packets are discarded.
            draining = true;
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket) {
            blinkTicks.incrementAndGet();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        progress.tick();
        float width = 100.0f;
        float height = 5.0f;
        float x = (mc.getWindow().getGuiScaledWidth() - width) / 2.0f;
        float y = mc.getWindow().getGuiScaledHeight() / 2.0f + 15.0f;
        float fillWidth = width * Mth.clamp(progress.getValueF(), 0.0f, 1.0f);

        RenderUtil.drawRoundedRect(event.poseStack(), x, y, width, height, 2.0f,
                new Color(0, 0, 0, 128).getRGB());
        if (fillWidth > 0.0f) {
            RenderUtil.drawRoundedRect(event.poseStack(), x, y, fillWidth, height, 2.0f, MAIN_COLOR);
        }
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        disableImmediately();
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent event) {
        disableImmediately();
    }

    private boolean canDrain() {
        return isEnabled()
                && !draining
                && !packets.isEmpty()
                && mc.player != null
                && mc.level != null
                && mc.getConnection() != null;
    }

    private void disableImmediately() {
        clearQueue();
        if (isEnabled()) {
            super.setEnabled(false);
        } else {
            removeFakePlayer();
        }
    }

    private boolean releaseTick() {
        Packet<?> packet;
        boolean releasedMove = false;
        while ((packet = packets.poll()) != null) {
            if (packet instanceof ServerboundMovePlayerPacket movePacket) {
                blinkTicks.decrementAndGet();
                releasedMove = true;
                handleMove(movePacket);
            }
            PacketUtil.sendQueued(packet);
            if (releasedMove) {
                break;
            }
        }
        return releasedMove;
    }

    private void handleMove(ServerboundMovePlayerPacket packet) {
        if (fakePlayer == null) {
            return;
        }
        float yaw = packet.getYRot(fakePlayer.getYRot());
        float pitch = packet.getXRot(fakePlayer.getXRot());
        fakePlayer.setPos(
                packet.getX(fakePlayer.getX()),
                packet.getY(fakePlayer.getY()),
                packet.getZ(fakePlayer.getZ())
        );
        if (packet.hasRotation()) {
            fakePlayer.setYRot(yaw);
            fakePlayer.setYHeadRot(yaw);
            fakePlayer.setXRot(pitch);
        }
    }

    private void createFakePlayer() {
        removeFakePlayer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), mc.player.getGameProfile().getName());
        profile.getProperties().putAll(mc.player.getGameProfile().getProperties());
        fakePlayer = new BlinkGhostPlayer(mc.level, profile);
        fakePlayer.setId(FAKE_PLAYER_ID);
        fakePlayer.copyPosition(mc.player);
        fakePlayer.setYHeadRot(mc.player.getYHeadRot());
        fakePlayer.setSprinting(mc.player.isSprinting());
        mc.level.addEntity(fakePlayer);
    }

    private void removeFakePlayer() {
        if (fakePlayer == null) {
            return;
        }
        ClientLevel level = mc.level;
        if (level != null && level.getEntity(fakePlayer.getId()) == fakePlayer) {
            level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
    }

    private void clearQueue() {
        packets.clear();
        blinkTicks.set(0);
    }

    private void updateProgress() {
        float target = Mth.clamp(
                blinkTicks.get() / (float) maxTicks.getValue().intValue(),
                0.0f,
                1.0f
        );
        if (Math.abs(progress.getToValue() - target) > 0.001) {
            progress.animate(target, 0.2);
        }
    }

    private boolean isPlayerInDanger() {
        if (fakePlayer == null) {
            return false;
        }
        double playerRange = playerDistance.getValue().doubleValue();
        double tntRange = tntDistance.getValue().doubleValue();
        double hitBoxExpand = projectileExpand.getValue().doubleValue();

        for (Player player : mc.level.players()) {
            if (player == mc.player || player == fakePlayer || Teams.isSameTeam(player)
                    || AntiBots.isBot(player) || AntiBots.isBedWarsBot(player)) {
                continue;
            }
            if (playerRange > 0.0 && distanceToBox(player.getEyePosition(), fakePlayer.getBoundingBox()) < playerRange) {
                return true;
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt && tntRange > 0.0 && fakePlayer.distanceTo(entity) <= tntRange) {
                return true;
            }
            if ((entity instanceof Arrow || entity instanceof ThrownEgg || entity instanceof Snowball)
                    && predictsProjectileHit(entity, hitBoxExpand)) {
                return true;
            }
        }
        return false;
    }

    private boolean predictsProjectileHit(Entity projectile, double expand) {
        if (projectile.distanceToSqr(fakePlayer) > 6400.0) {
            return false;
        }
        Vec3 position = projectile.position();
        Vec3 velocity = projectile.getDeltaMovement();
        AABB target = fakePlayer.getBoundingBox().inflate(expand);
        double gravity = projectile instanceof Arrow ? 0.05 : 0.03;

        for (int tick = 0; tick < 120; tick++) {
            Vec3 next = position.add(velocity);
            if (target.clip(position, next).isPresent()) {
                return true;
            }
            HitResult blockHit = mc.level.clip(new ClipContext(
                    position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            if (blockHit.getType() != HitResult.Type.MISS || next.y < mc.level.getMinY() - 16) {
                return false;
            }
            double drag = projectile.isInWater() ? 0.8 : 0.99;
            velocity = new Vec3(velocity.x * drag, velocity.y * drag - gravity, velocity.z * drag);
            position = next;
        }
        return false;
    }

    private static double distanceToBox(Vec3 point, AABB box) {
        double x = Mth.clamp(point.x, box.minX, box.maxX);
        double y = Mth.clamp(point.y, box.minY, box.maxY);
        double z = Mth.clamp(point.z, box.minZ, box.maxZ);
        return point.distanceTo(new Vec3(x, y, z));
    }

    public static final class BlinkGhostPlayer extends RemotePlayer {
        public BlinkGhostPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }
    }
}

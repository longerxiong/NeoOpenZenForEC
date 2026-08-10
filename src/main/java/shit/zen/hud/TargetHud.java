package shit.zen.hud;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.hud.target.RoundTargetStyle;
import shit.zen.hud.target.TargetStyle;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.event.EventTarget;

public class TargetHud
extends HudElement {
    private final SmoothAnimationTimer healthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer healthLagAnim = new SmoothAnimationTimer();
    public static final Map<String, AtomicInteger> playerHealthMap = new HashMap<>();
    private float lastHealth;
    private float healthDelta;
    private final ModeSetting styleMode = new ModeSetting("Mode", "Round").withDefault("Round");
    private String belowNameObjective;

    public TargetHud() {
        super("TargetHUD");
        this.setWidth(200.0f);
        this.setHeight(60.0f);
        TargetStyle.initStyles();
        this.setEnabled(true);
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        Packet<?> packet = packetEvent.getPacket();
        if (packet instanceof ClientboundSetDisplayObjectivePacket displayPacket) {
            if (displayPacket.getSlot() == DisplaySlot.BELOW_NAME) {
                String nextObjective = displayPacket.getObjectiveName().isEmpty()
                        ? null : displayPacket.getObjectiveName();
                if (this.belowNameObjective == null
                        ? nextObjective != null : !this.belowNameObjective.equals(nextObjective)) {
                    playerHealthMap.clear();
                }
                this.belowNameObjective = nextObjective;
            }
            return;
        }
        if (packet instanceof ClientboundSetScorePacket clientboundSetScorePacket) {
            boolean knownHealthObjective = "belowHealth".equals(clientboundSetScorePacket.objectiveName())
                    || "health".equals(clientboundSetScorePacket.objectiveName());
            boolean activeBelowName = clientboundSetScorePacket.objectiveName().equals(this.belowNameObjective);
            if (mc.level != null && mc.player != null && (knownHealthObjective || activeBelowName)
                    && !clientboundSetScorePacket.owner().equals(mc.player.getGameProfile().getName())) {
                playerHealthMap.computeIfAbsent(clientboundSetScorePacket.owner(), string -> new AtomicInteger()).set(clientboundSetScorePacket.score());
            }
            return;
        }
        if (packet instanceof ClientboundResetScorePacket resetPacket
                && (resetPacket.objectiveName().equals(this.belowNameObjective)
                || "belowHealth".equals(resetPacket.objectiveName())
                || "health".equals(resetPacket.objectiveName()))) {
            playerHealthMap.remove(resetPacket.owner());
        }
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent event) {
        playerHealthMap.clear();
        this.belowNameObjective = null;
    }

    @Override
    public void onSettings() {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        float maxHealth;
        LivingEntity target = null;
        if (mc.screen instanceof ChatScreen) {
            target = mc.player;
        } else if (KillAura.aimingTarget instanceof LivingEntity le) {
            target = le;
        }
        if (target != null) {
            float displayHealth = this.getDisplayHealth(target);
            if (!Mth.equal(this.lastHealth, displayHealth)) {
                this.healthDelta = displayHealth - this.lastHealth;
                this.lastHealth = displayHealth;
            }
            float currentHealth = Math.min(displayHealth, 20.0f);
            maxHealth = Math.min(target.getMaxHealth(), 20.0f);
            float ratio = maxHealth > 0.0f ? currentHealth / maxHealth : 0.0f;
            this.healthAnim.animate(ratio, 0.5, Easings.EASE_OUT_POW4);
            this.healthLagAnim.animate(ratio, 1.5, Easings.EASE_OUT_POW5);
        } else {
            this.healthDelta = 0.0f;
        }
        this.healthAnim.tick();
        this.healthLagAnim.tick();
        TargetStyle targetStyle = TargetStyle.getByName(this.styleMode.getValue());
        if (targetStyle != null) {
            maxHealth = target != null ? (target.getMaxHealth() > 0.0f
                                          ? Math.min(this.getDisplayHealth(target), 20.0f) / Math.min(target.getMaxHealth(), 20.0f)
                                          : 0.0f) : 0.0f;
            targetStyle.render(render2DEvent, target, this.healthAnim, this.healthLagAnim, maxHealth, x, y);
            if (targetStyle instanceof RoundTargetStyle) {
                this.setWidth(120.0f);
                this.setHeight(38.0f);
            } else {
                this.setWidth(150.0f);
                this.setHeight(36.0f);
            }
        }
    }

    private float getDisplayHealth(LivingEntity target) {
        if (target == null) {
            return 0.0f;
        }
        String profileName = target instanceof AbstractClientPlayer player
                ? player.getGameProfile().getName() : target.getName().getString();
        AtomicInteger direct = playerHealthMap.get(profileName);
        if (direct != null) {
            return Math.max(0.0f, direct.get());
        }

        // Scoreboard owners may contain decorative formatting/emoji. Compare a
        // normalized form only for lookup; the HUD renders the numeric score only.
        String normalizedName = normalizeScoreOwner(profileName);
        for (Map.Entry<String, AtomicInteger> entry : playerHealthMap.entrySet()) {
            if (normalizeScoreOwner(entry.getKey()).equals(normalizedName)) {
                return Math.max(0.0f, entry.getValue().get());
            }
        }
        return Math.max(0.0f, target.getHealth());
    }

    private static String normalizeScoreOwner(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_') {
                normalized.append(Character.toLowerCase(ch));
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }
}

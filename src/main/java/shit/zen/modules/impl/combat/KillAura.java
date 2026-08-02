package shit.zen.modules.impl.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.*;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.antikb.NoXZMode;
import shit.zen.modules.impl.player.AntiTNT;
import shit.zen.modules.impl.player.AntiWeb;
import shit.zen.modules.impl.player.Blink;
import shit.zen.modules.impl.player.Helper;
import shit.zen.modules.impl.player.MidPearl;
import shit.zen.modules.impl.player.Stuck;
import shit.zen.modules.impl.world.Teams;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.EntityUtil;
import shit.zen.utils.game.ItemUtil;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.math.MathUtil;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.event.EventTarget;

public class KillAura extends Module {
    public static KillAura INSTANCE;
    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targetList = new ArrayList<>();

    // Fields kept in sync with the obfuscated jar: 14 BooleanSetting / 8
    // NumberSetting / 3 ModeSetting, in declaration order.
    public final BooleanSetting attackPlayer    = new BooleanSetting("Attack Player", true);
    public final BooleanSetting attackInvisible = new BooleanSetting("Attack Invisible", false);
    public final BooleanSetting attackAnimals   = new BooleanSetting("Attack Animals", false);
    public final BooleanSetting attackMobs      = new BooleanSetting("Attack Mobs", true);
    public final BooleanSetting multiAttack     = new BooleanSetting("Multi Attack", true);
    public final BooleanSetting throughWalls    = new BooleanSetting("Through Walls", false);
    public final BooleanSetting infSwitch       = new BooleanSetting("Infinity Switch", false);
    public final BooleanSetting preferBaby      = new BooleanSetting("Prefer Baby", false);
    public final BooleanSetting morePart        = new BooleanSetting("More Particles", false);
    public final BooleanSetting keepSprint      = new BooleanSetting("Keep Sprint", true);
    public final BooleanSetting movementFix     = new BooleanSetting("Movement Fix", true);
    public final BooleanSetting ignoreSkipTicks = new BooleanSetting("Ignore skip ticks", false);
    public final BooleanSetting autoBlock   = new BooleanSetting("AutoBlock", true);
    public final ModeSetting autoBlockMode = new ModeSetting("AutoBlock Mode", "Fake", "Gapple").withDefault("Fake");
//    public final BooleanSetting test            = new BooleanSetting("Test", false);
    public final NumberSetting blockRange = new NumberSetting("Block Range", 4.0, 1.0, 20.0, 0.1, autoBlock::getValue);
    public final NumberSetting aimRange    = new NumberSetting("Aim Range", 4.0, 1.0, 20.0, 0.1);
    public final NumberSetting attackRange = new NumberSetting("Attack Range", 3.0, 1.0, 20.0, 0.1);
    public final NumberSetting maxAps      = new NumberSetting("Max APS", 12.0, 1.0, 20.0, 1.0);
    public final NumberSetting minAps      = new NumberSetting("Min APS", 9.0, 1.0, 20.0, 1.0);
    public final NumberSetting switchSize  = new NumberSetting("Switch Size", 1.0, 1.0, 5.0, 1.0,
            () -> !(Boolean) this.infSwitch.getValue());
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay (Attack Times)", 1.0, 1.0, 10.0, 1.0);
    public final NumberSetting fov         = new NumberSetting("FoV", 360.0, 10.0, 360.0, 1.0);
    public final NumberSetting hurtTime    = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    public final ModeSetting delayMode    = new ModeSetting("Delay Mode", "1.8", "1.9").withDefault("1.8");
    public final ModeSetting priorityMode = new ModeSetting("Priority", "Distance", "FoV", "Health", "None").withDefault("FoV");
    public final ModeSetting targetEsp    = new ModeSetting("Target ESP", "None", "Spiral", "Box", "Tab").withDefault("None");

    private RotationUtil.BestHitInfo currentBestHit;
    private RotationUtil.BestHitInfo prevBestHit;
    private int attackTimes;
    private float attacks;
    private int targetIndex;
    public int sprintTickCounter;
    private int sprintCounter;
    private boolean pendingStopBlock;
    public Rotation rotation;

    public KillAura() {
        super("KillAura", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.rotation = null;
        this.targetIndex = 0;
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        targetList.clear();
        blocking = false;
        this.pendingStopBlock = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        stopBlock();
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        this.sprintTickCounter = 0;
        this.sprintCounter = 0;
        this.attackTimes = 0;
        this.pendingStopBlock = false;
        super.onDisable();
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        target = null;
        aimingTarget = null;
        this.attacks = 0.0f;
        this.setEnabled(false);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (this.targetEsp.is("None")) return;
        Entity entity = aimingTarget;
        if (entity == null || mc.gameRenderer == null) return;
        PoseStack poseStack = event.poseStack();
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        Vec3 playerDelta = mc.player.getDeltaMovement();
        Vec3 offset = new Vec3(
                dx + playerDelta.x + 0.005,
                dy + playerDelta.y - 0.002,
                dz + playerDelta.z + 0.005);

        String mode = this.targetEsp.getValue();
        switch (mode) {
            case "Spiral" -> RenderUtil.drawSpiralEffect(poseStack, entity, event.partialTick());
            case "Box" -> {
                int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                Color color;
                if (hurtTime == 0) {
                    color = new Color(0, 0, 0, 130);
                } else if (hurtTime >= 9 && hurtTime <= 10) {
                    color = new Color(0, 255, 255, 200);
                } else {
                    color = new Color(255, 0, 0, 200);
                }
                AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                AABB padded = new AABB(
                        base.minX - 0.175, base.minY - 0.125, base.minZ - 0.175,
                        base.maxX + 0.175, base.maxY + 0.225, base.maxZ + 0.175);
                RenderUtil.drawFilledColoredBox(padded, poseStack, color, color);
            }
            case "Tab" -> {
                int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                Color color;
                if (hurtTime == 0) {
                    color = new Color(0, 0, 0, 130);
                } else if (hurtTime == 3) {
                    color = new Color(255, 255, 255, 200);
                } else {
                    color = new Color(255, 0, 0, 200);
                }
                AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                AABB band = new AABB(
                        base.minX, base.minY + entity.getEyeHeight() + 0.11, base.minZ,
                        base.maxX, base.maxY - 0.13, base.maxZ);
                RenderUtil.drawFilledColoredBox(band, poseStack, color, color);
            }
            default -> {
            }
        }
    }

    private static final int GAPPLE_BAR_COLOR = new Color(238, 204, 51, 255).getRGB();
    private static final int GAPPLE_BG_COLOR = new Color(0, 0, 0, 128).getRGB();

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        // Only show progress bar when Gapple autoblock is active and player is eating
        if (!autoBlock.getValue() || !autoBlockMode.is("Gapple") || !blocking) return;
        int remainingTicks = mc.player.getUseItemRemainingTicks();
        if (remainingTicks <= 0) return;

        float maxTicks = 32.0f;
        float progress = Mth.clamp(1.0f - (remainingTicks / maxTicks), 0.0f, 1.0f);

        float width = 100.0f;
        float height = 5.0f;
        float x = (mc.getWindow().getGuiScaledWidth() - width) / 2.0f;
        float y = mc.getWindow().getGuiScaledHeight() / 3.0f;
        float fillWidth = width * progress;

        // "Eating Gapple..." text
        String label = "Eating Gapple...";
        int labelX = (int) (mc.getWindow().getGuiScaledWidth() / 2.0f - mc.font.width(label) / 2.0f);
        int labelY = (int) (y - mc.font.lineHeight - 2);
        event.guiGraphics().drawString(mc.font, label, labelX, labelY, 0xFFFFFFFF);

        // Progress bar
        RenderUtil.drawRoundedRect(event.poseStack(), x, y, width, height, 2.0f, GAPPLE_BG_COLOR);
        if (fillWidth > 0.0f) {
            RenderUtil.drawRoundedRect(event.poseStack(), x, y, fillWidth, height, 2.0f, GAPPLE_BAR_COLOR);
        }
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (this.keepSprint.getValue()) {
            ++this.sprintTickCounter;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!ZenClient.isReady()) {
            return;
        }

        // AutoBlock: block/unblock based on distance to target
        if (autoBlock.getValue() && mc.player != null) {
            Entity blockTarget = aimingTarget != null ? aimingTarget : target;
            if (blockTarget != null && mc.player.distanceTo(blockTarget) <= getBlockRange()) {
                if (!this.pendingStopBlock) {
                    doBlock();
                }
            } else {
                stopBlock();
            }
            // Gapple mode: wait until apple is finished eating before releasing useKey
            if (this.pendingStopBlock) {
                if (mc.player.getUseItemRemainingTicks() <= 0) {
                    mc.options.keyUse.setDown(false);
                    this.pendingStopBlock = false;
                    blocking = false;
                }
            }
        }

        if (mc.screen instanceof AbstractContainerScreen
                || ItemUtil.hasServerItem()
                || (Stuck.INSTANCE != null && Stuck.INSTANCE.isEnabled())
                || (Helper.INSTANCE != null && Helper.INSTANCE.isEnabled() && Helper.targetRotation != null)
                || AntiWeb.targetRotation != null
                || AntiTNT.targetRotation != null
                || MidPearl.targetRotation != null
                || this.isWebPlacing()) {
            target = null;
            aimingTarget = null;
            this.currentBestHit = null;
            this.rotation = null;
            this.prevBestHit = null;
            targetList.clear();
            this.sprintTickCounter = 0;
            this.attacks = 0.0f;
            this.sprintCounter = 0;
            return;
        }
        boolean isSwitch = this.switchSize.getValue().intValue() > 1
                || this.infSwitch.getValue()
                || this.multiAttack.getValue();
        this.updateTargets();
        aimingTarget = this.getTarget();
        this.prevBestHit = this.currentBestHit;
        this.currentBestHit = null;
        if (aimingTarget != null) {
            this.currentBestHit = RotationUtil.getBestHit(aimingTarget, this.getTargetRange(), this.throughWalls.getValue());
            this.rotation = this.currentBestHit != null ? this.currentBestHit.rotation() : null;
        } else {
            this.rotation = null;
        }
        if (targetList.isEmpty()) {
            target = null;
            return;
        }
        if (this.targetIndex > targetList.size() - 1) {
            this.targetIndex = 0;
        }
        if (targetList.size() > 1
                && (this.attackTimes >= this.switchDelay.getValue().intValue()
                    || (this.currentBestHit != null && this.currentBestHit.distance() > this.getAttackRange()))) {
            this.attackTimes = 0;
            for (int i = 0; i < targetList.size(); ++i) {
                ++this.targetIndex;
                if (this.targetIndex > targetList.size() - 1) {
                    this.targetIndex = 0;
                }
                Entity nextTarget = targetList.get(this.targetIndex);
                RotationUtil.BestHitInfo nextHit = RotationUtil.getBestHit(nextTarget, this.getTargetRange(), this.throughWalls.getValue());
                if (nextHit != null && nextHit.distance() <= this.getAttackRange()) {
                    break;
                }
            }
        }
        if (this.targetIndex > targetList.size() - 1 || !isSwitch) {
            this.targetIndex = 0;
        }
        target = targetList.get(this.targetIndex);
        if (this.delayMode.is("1.8")) {
            float apsValue;
            float minApsValue;
            if (NoXZMode.isAttacking) {
                int kbAttackAmount = AntiKB.INSTANCE != null
                        ? AntiKB.INSTANCE.attackAmount.getValue().intValue()
                        : 0;
                apsValue = this.maxAps.getValue().floatValue() - kbAttackAmount;
                minApsValue = this.minAps.getValue().floatValue() - kbAttackAmount;
            } else {
                apsValue = this.maxAps.getValue().floatValue();
                minApsValue = this.minAps.getValue().floatValue();
            }
            if (this.keepSprint.getValue()) {
                apsValue *= 2.0f;
                minApsValue *= 2.0f;
            }
            this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
        } else if (this.sprintCounter > 0) {
            this.sprintCounter--;
        } else if (mc.player.getAttackStrengthScale(0.0f) >= 0.9f) {
            this.doAttack();
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }
        if (/*mc.player.getUseItem().isEmpty() || autoBlock.getValue()
                && mc.screen == null
                &&*/ (this.ignoreSkipTicks.getValue() || ClientBase.delayPackets.isEmpty())) {
            while (this.attacks >= 1.0f) {
                this.doAttack();
                this.attacks -= 1.0f;
            }
        } else {
            this.attacks = 0.0f;
        }
    }

    public void doAttack() {
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }
        if (targetList.isEmpty()) return;

        Rotation aimRot = RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : this.rotation;
        if (mc.player == null || aimRot == null) return;

        double attackRange = this.getAttackRange();
        HitResult hitResult = RotationUtil.performRaycast(aimRot, attackRange, this.throughWalls.getValue());
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (AntiBots.isBot(hitEntity)) {
                ChatUtil.print("Skipped attack on suspected bot");
                return;
            }
            if (!targetList.contains(hitEntity)) {
                return;
            }
        }
        if (this.multiAttack.getValue()) {
            int attacked = 0;
            for (Entity entity : targetList) {
                if (RotationUtil.getHitDistance(entity, mc.player.getEyePosition(), aimRot) > attackRange) continue;
                this.attackEntity(entity);
                if (++attacked >= 2) break;
            }
        } else if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            this.attackEntity(hitEntity);
        }
    }

    public Entity getTarget() {
        Entity entity = target;
        if (entity == null) {
            List<Entity> list = this.getTargets();
            if (!list.isEmpty()) {
                entity = list.get(0);
            }
        }
        if (entity != null) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(entity)) {
                return null;
            }
        }
        return entity;
    }

    public void updateTargets() {
        List<Entity> next = this.getTargets();
        targetList = next != null ? next : new ArrayList<>();
    }

    public boolean isValidTarget(Entity entity) {
        if (!ZenClient.isReady()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof Blink.BlinkGhostPlayer) return false;
        if (entity instanceof LivingEntity livingEntity) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
                return false;
            }
            if (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !(Boolean) this.attackInvisible.getValue()) return false;
//            if (entity instanceof Player player) {
//                if (this.test.getValue() && player.getY() >= mc.player.getY() + 0.05f) {
//                    return true;
//                }
//                // ZenClient.isOwner() was stripped during deobfuscation; the
//                // original jar bailed here when the entity name matched the
//                // client owner. Re-enable once that helper is restored.
//            }
            if (Teams.isSameTeam(entity)) return false;
            if (entity instanceof Player && !(Boolean) this.attackPlayer.getValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || livingEntity.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                    && !(Boolean) this.attackMobs.getValue()) {
                return false;
            }
            if ((entity instanceof Animal || entity instanceof Squid) && !(Boolean) this.attackAnimals.getValue()) {
                return false;
            }
            if (entity instanceof Villager && !(Boolean) this.attackAnimals.getValue()) return false;
            return !(entity instanceof Player) || !entity.isSpectator();
        }
        return false;
    }

    public boolean isValidAttack(Entity entity) {
        if (mc.player == null) return false;
        if (!this.isValidTarget(entity)) return false;
        if (entity instanceof LivingEntity le && le.hurtTime > this.hurtTime.getValue().intValue()) {
            return false;
        }
        Vec3 vec3 = RotationUtil.closestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
        if (vec3.distanceTo(mc.player.getEyePosition()) > this.getTargetRange()) {
            return false;
        }
        if (!RotationUtil.isEntityInFov(entity, this.fov.getValue().floatValue() / 2.0f)) {
            return false;
        }
        return true;
    }

    private double getAttackRange() {
        return this.attackRange.getValue().doubleValue();
    }

    private double getTargetRange() {
        return Math.max(this.aimRange.getValue().doubleValue(), this.getAttackRange());
    }

    private double getBlockRange() {
        return this.blockRange.getValue().doubleValue();
    }

    public void attackEntity(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return;
        if (this.isWebPlacing()) return;

        ++this.attackTimes;

        int attackKey = mc.options.keyAttack.getKey().getValue();
        if (this.keepSprint.getValue()) {
            if (this.sprintTickCounter % 2 == 0) {
                mc.gameMode.attack(mc.player, entity);
                NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Pre(attackKey, 1, 0));
                mc.player.swing(InteractionHand.MAIN_HAND);
                NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(attackKey, 1, 0));
            }
        } else {
            mc.gameMode.attack(mc.player, entity);
            NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Pre(attackKey, 1, 0));
            mc.player.swing(InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(attackKey, 1, 0));
        }

        if (this.morePart.getValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        if (this.delayMode.is("1.9")) {
            this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
        }
    }

    private boolean blocking;

    private void doBlock() {
        if (mc.player == null || blocking) return;
        switch(autoBlockMode.getValue()){
            case "Fake" ->{}
            case "Gapple" ->{
                mc.player.getOffhandItem();
                if(mc.player.getOffhandItem().getItem() == Items.GOLDEN_APPLE) {
                    mc.options.keyUse.setDown(true);
                }
            }
        }
        blocking = true;
    }

    private void stopBlock() {
        if (mc.player == null || !blocking) return;
        switch(autoBlockMode.getValue()){
            case "Fake" ->{}
            case "Gapple" ->{
                int remainingTicks = mc.player.getUseItemRemainingTicks();
                // If currently eating, defer unblock until finished
                if (remainingTicks > 0) {
                    this.pendingStopBlock = true;
                    return;
                }
                // Not eating, release immediately
                mc.options.keyUse.setDown(false);
                this.pendingStopBlock = false;
                blocking = false;
                return;
            }
        }
        blocking = false;
    }

    private boolean isWebPlacing() {
//        return AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled() && AutoWebPlace.targetRotation != null;
        return false;
    }

    private List<Entity> getTargets() {
        if (mc.player == null || mc.level == null) {
            return new ArrayList<>();
        }
        Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
                .filter(this::isValidAttack);
        List<Entity> possibleTargets = stream.collect(Collectors.toList());
        possibleTargets.removeIf(entity -> !this.canAcquireTarget(entity));
        if (this.priorityMode.is("Distance")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getDistanceToPlayer));
        } else if (this.priorityMode.is("FoV")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getAngleDiffToTarget));
        } else if (this.priorityMode.is("Health")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getEntityHealth));
        }
        if (this.preferBaby.getValue()
                && possibleTargets.stream().anyMatch(KillAura::isBaby)) {
            possibleTargets.removeIf(KillAura::isNotBaby);
        }
        possibleTargets.sort(Comparator.comparing(KillAura::getCrystalPriority));
        if (this.infSwitch.getValue()) {
            return possibleTargets;
        }
        int limit = (int) Math.min(possibleTargets.size(), this.switchSize.getValue().intValue());
        return new ArrayList<>(possibleTargets.subList(0, limit));
    }

    private boolean canAcquireTarget(Entity entity) {
        if (this.throughWalls.getValue()) {
            return true;
        }
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 hitPoint = RotationUtil.closestPoint(eyePos, entity.getBoundingBox());
        Rotation hitRotation = RotationUtil.exactRotation(eyePos, hitPoint);
        HitResult hitResult = RotationUtil.performRaycast(hitRotation, this.getTargetRange());
        return hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() == entity;
    }

    private static Integer getCrystalPriority(Entity entity) {
        return entity instanceof EndCrystal ? 0 : 1;
    }

    private static boolean isNotBaby(Entity entity) {
        return !(entity instanceof LivingEntity) || !((LivingEntity) entity).isBaby();
    }

    private static boolean isBaby(Entity entity) {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby();
    }

    private static double getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity le) {
            return le.getHealth();
        }
        return 0.0;
    }

    private static double getAngleDiffToTarget(Entity entity) {
        return RotationUtil.angleDiff(RotationHandler.targetRotation.getYaw(), RotationUtil.entityRotation(entity).getYaw());
    }

    private static double getDistanceToPlayer(Entity entity) {
        return entity.distanceTo(mc.player);
    }

    private static boolean isLivingEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }
}

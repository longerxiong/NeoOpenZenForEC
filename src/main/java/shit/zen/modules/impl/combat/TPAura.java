package shit.zen.modules.impl.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.ReceivePacketEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.Blink;
import shit.zen.modules.impl.player.Stuck;
import shit.zen.modules.impl.world.Teams;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.math.MathUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.render.WorldOverlayRenderer;
import shit.zen.utils.rotation.Rotation;

public class TPAura extends Module {
    private static final double[] TELEPORT_DISTANCES = {3.0, 2.5, 2.0, 1.5};
    private static final double[] ANGLE_OFFSETS = {0.0, 30.0, -30.0, 60.0, -60.0};
    private static final double[] HEIGHT_OFFSETS = {0.25, 0.5, 1.0};
    private static final double MAX_ATTACK_DISTANCE = 3.0;
    private static final Direction[] PATH_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST,
            Direction.EAST, Direction.UP, Direction.DOWN
    };
    private static final int PLAN_REFRESH_TICKS = 5;
    private static final int MAX_EXPANDED_NODES = 20_000;
    private static final int PATH_COLOR = new Color(36, 32, 147, 255).getRGB();
    private static final Color GHOST_FILL = new Color(36, 32, 147, 87);
    private static final Color GHOST_OUTLINE = new Color(36, 32, 147, 255);

    public static TPAura INSTANCE;

    public final BooleanSetting attackPlayer = new BooleanSetting("Attack Player", true);
    public final BooleanSetting attackInvisible = new BooleanSetting("Attack Invisible", false);
    public final BooleanSetting attackAnimals = new BooleanSetting("Attack Animals", false);
    public final BooleanSetting attackMobs = new BooleanSetting("Attack Mobs", true);
    public final BooleanSetting moreParticles = new BooleanSetting("More Particles", false);
    public final BooleanSetting keepSprint = new BooleanSetting("Keep Sprint", true);
    public final BooleanSetting render = new BooleanSetting("Render", true);
    public final NumberSetting maxAps = new NumberSetting("Max APS", 12.0, 1.0, 20.0, 1.0);
    public final NumberSetting minAps = new NumberSetting("Min APS", 9.0, 1.0, 20.0, 1.0);
    public final NumberSetting hurtTime = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay (Ticks)", 10.0, 0.0, 200.0, 1.0);
    public final NumberSetting maximumDistance = new NumberSetting("Maximum Distance", 95.0, 10.0, 250.0, 5.0);
    public final NumberSetting maximumCost = new NumberSetting("Maximum Cost", 250.0, 50.0, 500.0, 10.0);
    public final NumberSetting packetStep = new NumberSetting("Packet Step", 3.0, 1.0, 7.0, 1.0);
    public final ModeSetting delayMode = new ModeSetting("Delay Mode", "1.8", "1.9").withDefault("1.8");
    public final ModeSetting priorityMode = new ModeSetting("Priority", "Distance", "Health").withDefault("Distance");

    private float attacks;
    private int delayTicksRemaining;
    private int planRefreshTicks;
    private AttackPlan currentPlan;

    public TPAura() {
        super("TPAura", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.attacks = 0.0f;
        this.delayTicksRemaining = 0;
        this.planRefreshTicks = 0;
        this.currentPlan = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.attacks = 0.0f;
        this.delayTicksRemaining = 0;
        this.planRefreshTicks = 0;
        this.currentPlan = null;
        super.onDisable();
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        this.setEnabled(false);
    }

    @EventTarget
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.currentPlan = null;
            this.planRefreshTicks = 0;
            this.attacks = 0.0f;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.canRun()) {
            this.attacks = 0.0f;
            this.currentPlan = null;
            return;
        }

        this.refreshPlan(false);

        if (this.delayTicksRemaining > 0 && --this.delayTicksRemaining > 0) {
            return;
        }

        if (this.delayMode.is("1.9")) {
            if (mc.player.getAttackStrengthScale(0.0f) >= 0.9f) {
                this.tryAttack();
            }
            return;
        }

        float min = this.minAps.getValue().floatValue();
        float max = this.maxAps.getValue().floatValue();
        this.attacks = Math.min(1.0f, this.attacks
                + (float) (MathUtil.randomDouble(Math.min(min, max), Math.max(min, max)) / 20.0));
        if (this.attacks >= 1.0f && this.tryAttack()) {
            this.attacks = 0.0f;
        }
    }

    private boolean canRun() {
        return ZenClient.isReady()
                && mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.getConnection() != null
                && !mc.player.isSpectator()
                && !(Stuck.INSTANCE != null && Stuck.INSTANCE.isEnabled());
    }

    private boolean tryAttack() {
        if (this.currentPlan == null || !this.isPlanValid(this.currentPlan)) {
            this.refreshPlan(true);
        }
        AttackPlan plan = this.currentPlan;
        if (plan == null) {
            return false;
        }

        this.attackFrom(plan);
        this.delayTicksRemaining = this.delay.getValue().intValue();
        return true;
    }

    private void attackFrom(AttackPlan plan) {
        Entity target = plan.target();
        Vec3 teleportPos = plan.teleportPosition();
        Vec3 targetPoint = RotationUtil.closestPoint(
                teleportPos.add(0.0, mc.player.getEyeHeight(), 0.0), target.getBoundingBox());
        Rotation rotation = RotationUtil.exactRotation(
                teleportPos.add(0.0, mc.player.getEyeHeight(), 0.0), targetPoint);

        this.sendPathForward(plan.path(), rotation);
        try {
            PacketUtil.sendQueued(ServerboundInteractPacket.createAttackPacket(target, mc.player.isShiftKeyDown()));

            mc.player.attack(target);
            mc.player.resetAttackStrengthTicker();
            int attackKey = mc.options.keyAttack.getKey().getValue();
            NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Pre(attackKey, 1, 0));
            mc.player.swing(InteractionHand.MAIN_HAND, false);
            PacketUtil.sendQueued(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(attackKey, 1, 0));

            if (this.moreParticles.getValue()) {
                mc.player.magicCrit(target);
                mc.player.crit(target);
            }
        } finally {
            this.sendPathBack(plan.path());
        }
    }

    private void refreshPlan(boolean force) {
        if (!force && this.currentPlan != null && this.isPlanValid(this.currentPlan)
                && this.planRefreshTicks-- > 0) {
            return;
        }
        this.currentPlan = this.findAttackPlan();
        this.planRefreshTicks = PLAN_REFRESH_TICKS;
    }

    private AttackPlan findAttackPlan() {
        Vec3 origin = mc.player.position();
        double maximumDistanceSqr = this.maximumDistance.getValue().doubleValue();
        maximumDistanceSqr *= maximumDistanceSqr;
        for (Entity target : this.getTargets()) {
            if (target.distanceToSqr(mc.player) > maximumDistanceSqr) {
                continue;
            }
            Vec3 teleportPosition = this.findTeleportPosition(target);
            if (teleportPosition == null) {
                continue;
            }
            List<Vec3> path = this.findPath(origin, teleportPosition);
            if (!path.isEmpty()) {
                return new AttackPlan(target, teleportPosition, path);
            }
        }
        return null;
    }

    private boolean isPlanValid(AttackPlan plan) {
        if (!this.isValidTarget(plan.target())
                || !this.isSafeAirPosition(plan.target(), plan.teleportPosition())) {
            return false;
        }
        List<Vec3> path = plan.path();
        if (path.size() < 2 || mc.player.position().distanceToSqr(path.get(0)) > 0.01) {
            return false;
        }
        for (int i = 1; i < path.size(); i++) {
            if (!this.isSegmentClear(path.get(i - 1), path.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<Vec3> findPath(Vec3 origin, Vec3 destination) {
        BlockPos start = BlockPos.containing(origin);
        BlockPos goal = BlockPos.containing(destination);
        if (start.equals(goal)) {
            return this.isSegmentClear(origin, destination)
                    ? List.of(origin, destination)
                    : List.of();
        }

        PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(PathNode::estimatedTotalCost));
        Map<BlockPos, Double> bestCosts = new HashMap<>();
        PathNode startNode = new PathNode(start, 0.0, this.pathHeuristic(start, goal), null);
        open.add(startNode);
        bestCosts.put(start, 0.0);
        int expanded = 0;
        double maxCost = this.maximumCost.getValue().doubleValue();

        while (!open.isEmpty() && expanded++ < MAX_EXPANDED_NODES) {
            PathNode current = open.poll();
            double knownCost = bestCosts.getOrDefault(current.position(), Double.POSITIVE_INFINITY);
            if (current.cost() > knownCost) {
                continue;
            }
            if (current.position().equals(goal)) {
                return this.buildPacketPath(current, origin, destination);
            }

            for (Direction direction : PATH_DIRECTIONS) {
                BlockPos next = current.position().relative(direction);
                double nextCost = current.cost() + 1.0;
                if (nextCost > maxCost || !this.isWithinPathBounds(start, next)
                        || !this.isPathPositionSafe(next)) {
                    continue;
                }
                if (nextCost >= bestCosts.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                bestCosts.put(next, nextCost);
                open.add(new PathNode(next, nextCost, nextCost + this.pathHeuristic(next, goal), current));
            }
        }
        return List.of();
    }

    private List<Vec3> buildPacketPath(PathNode end, Vec3 origin, Vec3 destination) {
        List<Vec3> rawPath = new ArrayList<>();
        for (PathNode node = end; node != null; node = node.parent()) {
            rawPath.add(Vec3.atBottomCenterOf(node.position()));
        }
        Collections.reverse(rawPath);
        rawPath.set(0, origin);
        rawPath.add(destination);

        List<Vec3> packetPath = new ArrayList<>();
        packetPath.add(origin);
        int index = 0;
        double maxStep = this.packetStep.getValue().doubleValue();
        while (index < rawPath.size() - 1) {
            int best = index;
            for (int candidate = index + 1; candidate < rawPath.size(); candidate++) {
                if (rawPath.get(index).distanceTo(rawPath.get(candidate)) > maxStep) {
                    break;
                }
                if (this.isSegmentClear(rawPath.get(index), rawPath.get(candidate))) {
                    best = candidate;
                }
            }
            if (best == index) {
                return List.of();
            }
            packetPath.add(rawPath.get(best));
            index = best;
        }
        return packetPath;
    }

    private boolean isWithinPathBounds(BlockPos start, BlockPos position) {
        if (position.getY() < mc.level.getMinY() || position.getY() >= mc.level.getMaxY()) {
            return false;
        }
        double maxDistance = this.maximumDistance.getValue().doubleValue();
        double dx = position.getX() - start.getX();
        double dy = position.getY() - start.getY();
        double dz = position.getZ() - start.getZ();
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    private boolean isPathPositionSafe(BlockPos position) {
        if (!mc.level.hasChunkAt(position)) {
            return false;
        }
        Vec3 point = Vec3.atBottomCenterOf(position);
        AABB box = this.playerBoxAt(point);
        return mc.level.noCollision(mc.player, box)
                && mc.level.getBlockStates(box).noneMatch(state -> !state.getFluidState().isEmpty());
    }

    private boolean isSegmentClear(Vec3 from, Vec3 to) {
        AABB sweptBox = this.playerBoxAt(from).minmax(this.playerBoxAt(to));
        return mc.level.noCollision(mc.player, sweptBox)
                && mc.level.getBlockStates(sweptBox).noneMatch(state -> !state.getFluidState().isEmpty());
    }

    private AABB playerBoxAt(Vec3 position) {
        return mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(position).deflate(1.0E-4);
    }

    private double pathHeuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getY() - to.getY())
                + Math.abs(from.getZ() - to.getZ());
    }

    private void sendPathForward(List<Vec3> path, Rotation rotation) {
        for (int i = 1; i < path.size(); i++) {
            Vec3 position = path.get(i);
            if (i == path.size() - 1) {
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.PosRot(
                        position.x, position.y, position.z,
                        rotation.getYaw(), rotation.getPitch(), false, false));
            } else {
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Pos(
                        position.x, position.y, position.z, false, false));
            }
        }
    }

    private void sendPathBack(List<Vec3> path) {
        for (int i = path.size() - 2; i >= 0; i--) {
            Vec3 position = path.get(i);
            if (i == 0) {
                // The grounded return packet clears fall distance accumulated by vertical path segments.
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.PosRot(
                        position.x, position.y, position.z,
                        mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            } else {
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Pos(
                        position.x, position.y, position.z, false, false));
            }
        }
    }

    private List<Entity> getTargets() {
        List<Entity> targets = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(this::isValidTarget)
                .collect(Collectors.toCollection(ArrayList::new));
        if (this.priorityMode.is("Health")) {
            targets.sort(Comparator.comparingDouble(entity -> ((LivingEntity) entity).getHealth()));
        } else {
            targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)));
        }
        return targets;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == mc.player || !entity.isAttackable()) {
            return false;
        }
        if (entity instanceof Blink.BlinkGhostPlayer || entity instanceof ArmorStand) {
            return false;
        }
        AntiBots antiBots = AntiBots.INSTANCE;
        if (antiBots != null && antiBots.isEnabled()
                && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
            return false;
        }
        if (living.isDeadOrDying() || living.getHealth() <= 0.0f
                || living.hurtTime > this.hurtTime.getValue().intValue()) {
            return false;
        }
        if (entity.isInvisible() && !this.attackInvisible.getValue()) {
            return false;
        }
        if (Teams.isSameTeam(entity)) {
            return false;
        }
        if (entity instanceof Player player) {
            return this.attackPlayer.getValue()
                    && !player.isSpectator()
                    && player.getBbWidth() >= 0.5
                    && !player.isSleeping();
        }
        if (entity instanceof Animal || entity instanceof Squid || entity instanceof Villager) {
            return this.attackAnimals.getValue();
        }
        if (entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem) {
            return this.attackMobs.getValue();
        }
        return false;
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (!this.render.getValue() || this.currentPlan == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }
        List<Vec3> renderPath = this.currentPlan.path().stream()
                .map(point -> point.add(0.0, 0.05, 0.0))
                .toList();
        WorldOverlayRenderer.drawLineStrip(renderPath, PATH_COLOR);
        this.renderGhostPlayer(event, this.currentPlan);

        AABB box = this.playerBoxAt(this.currentPlan.teleportPosition());
        RenderUtil.drawSolidBox(box, event.poseStack(), GHOST_FILL, GHOST_FILL.getAlpha() / 255.0f);
        RenderUtil.drawOutlineBox(box, event.poseStack(), GHOST_OUTLINE, 1.0f);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void renderGhostPlayer(RenderEvent event, AttackPlan plan) {
        Vec3 position = plan.teleportPosition();
        Vec3 eyePosition = position.add(0.0, mc.player.getEyeHeight(), 0.0);
        Vec3 targetPoint = RotationUtil.closestPoint(eyePosition, plan.target().getBoundingBox());
        Rotation rotation = RotationUtil.exactRotation(eyePosition, targetPoint);
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return;
        }
        EntityModel model = livingRenderer.getModel();
        LivingEntityRenderState renderState = (LivingEntityRenderState) livingRenderer.createRenderState(
                mc.player, event.partialTick());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.poseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(livingRenderer.getTextureLocation(renderState));

        poseStack.pushPose();
        try {
            poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - rotation.getYaw()));
            poseStack.scale(-1.0f, -1.0f, 1.0f);
            poseStack.translate(0.0f, -1.501f, 0.0f);
            model.setupAnim(renderState);
            VertexConsumer consumer = buffers.getBuffer(type);
            model.renderToBuffer(poseStack, consumer, 0xF000F0,
                    LivingEntityRenderer.getOverlayCoords(renderState, 0.0f), GHOST_FILL.getRGB());
        } finally {
            poseStack.popPose();
            WorldOverlayRenderer.withIdentityModelView(() -> buffers.endBatch(type));
        }
    }

    private Vec3 findTeleportPosition(Entity target) {
        double baseYaw = Math.toRadians(target.getYRot() + 180.0);
        for (double distance : TELEPORT_DISTANCES) {
            for (double angleOffset : ANGLE_OFFSETS) {
                double angle = baseYaw + Math.toRadians(angleOffset);
                double x = target.getX() - Math.sin(angle) * distance;
                double z = target.getZ() + Math.cos(angle) * distance;
                for (double heightOffset : HEIGHT_OFFSETS) {
                    Vec3 candidate = new Vec3(x, target.getY() + heightOffset, z);
                    if (this.isSafeAirPosition(target, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeAirPosition(Entity target, Vec3 position) {
        AABB box = this.playerBoxAt(position);
        if (!mc.level.noCollision(mc.player, box) || !mc.level.noCollision(mc.player, box.move(0.0, -0.1, 0.0))) {
            return false;
        }
        if (mc.level.getBlockStates(box).anyMatch(state -> !state.getFluidState().isEmpty())) {
            return false;
        }
        Vec3 eyePosition = position.add(0.0, mc.player.getEyeHeight(), 0.0);
        Vec3 hitPoint = RotationUtil.closestPoint(eyePosition, target.getBoundingBox());
        return eyePosition.distanceTo(hitPoint) <= MAX_ATTACK_DISTANCE;
    }

    private record AttackPlan(Entity target, Vec3 teleportPosition, List<Vec3> path) {
    }

    private record PathNode(BlockPos position, double cost, double estimatedTotalCost, PathNode parent) {
    }
}

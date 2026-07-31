package shit.zen.modules.impl.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.RayTraceUtil;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.game.PlayerUtil;
import shit.zen.utils.math.Vector2f;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.render.ProjectionUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

/** Automatically aims at and breaks nearby beds. */
public class BedBreaker extends Module {
    private enum BreakStage {
        IDLE,
        BREAKING,
        WAITING_FOR_SERVER
    }

    public static BedBreaker INSTANCE;

    public final NumberSetting range = new NumberSetting("Range", 4.5, 1.0, 6.0, 0.1);
    public final BooleanSetting allowNoRotation = new BooleanSetting("Allow NoRotation", false);
    public final ModeSetting moveFix = new ModeSetting("Move Fix", "Silent", "Strict", "None").withDefault("Silent");
    public final BooleanSetting hypixel = new BooleanSetting("Hypixel", false);
    public final BooleanSetting renderBreakBlock = new BooleanSetting("Render Break Block", true);
    public final BooleanSetting renderBreakProgress = new BooleanSetting("Render Break Progress", true);

    public Rotation targetRotation;
    private BlockPos bedPosition;
    private BlockPos targetBlock;
    private BlockPos breakingBlock;
    private Direction breakingDirection = Direction.UP;
    private BreakStage breakStage = BreakStage.IDLE;
    private float breakProgress;
    private int serverWaitTicks;
    private int previousSlot = -1;
    private Vector2f progressScreenPosition;

    public BedBreaker() {
        super("BedBreaker", Category.WORLD);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    public RotationHandler.MovementFixMode getMovementFixMode() {
        if ("Strict".equals(this.moveFix.getValue())) {
            return RotationHandler.MovementFixMode.STRICT;
        }
        if ("None".equals(this.moveFix.getValue())) {
            return RotationHandler.MovementFixMode.NONE;
        }
        return RotationHandler.MovementFixMode.SILENT;
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            this.reset();
            return;
        }

        if (this.bedPosition == null || !this.isBed(this.bedPosition)
                || !this.isInRange(this.bedPosition)) {
            this.bedPosition = this.findBed();
        }
        if (this.bedPosition == null) {
            this.cancelBreaking();
            this.restoreMiningSlot();
            this.targetBlock = null;
            this.targetRotation = null;
            return;
        }

        BlockPos nextTarget = this.hypixel.getValue() ? this.findBedDefense(this.bedPosition) : null;
        if (nextTarget == null) {
            nextTarget = this.bedPosition;
        }
        if (!Objects.equals(this.targetBlock, nextTarget)) {
            this.cancelBreaking();
            this.restoreMiningSlot();
        }
        this.targetBlock = nextTarget;
        this.targetRotation = RotationUtil.exactRotation(
                mc.player.getEyePosition(1.0f), Vec3.atCenterOf(this.targetBlock));

        if (this.breakStage == BreakStage.WAITING_FOR_SERVER) {
            if (mc.level.getBlockState(this.targetBlock).isAir()) {
                this.clearBreakingState();
            } else if (++this.serverWaitTicks >= 4) {
                this.clearBreakingState();
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        // MotionEvent phase names are inverted in this project: pre is fired after sendPosition.
        if (!event.isPre()) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || this.targetBlock == null || !this.isInRange(this.targetBlock)) {
            this.cancelBreaking();
            return;
        }

        Rotation activeRotation = RotationHandler.targetRotation;
        if (!RotationHandler.isRotating || activeRotation == null) {
            this.cancelBreaking();
            return;
        }
        boolean ownRotation = activeRotation == this.targetRotation;
        if (!ownRotation && !this.allowNoRotation.getValue()) {
            this.cancelBreaking();
            return;
        }

        Rotation rayRotation = ownRotation ? activeRotation : this.targetRotation;
        HitResult hitResult = RayTraceUtil.rayTrace(this.range.getValue().doubleValue(), 1.0f, false, rayRotation);
        Direction direction;
        if (hitResult instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().equals(this.targetBlock)) {
            direction = blockHit.getDirection();
        } else {
            if (this.hypixel.getValue()) {
                this.cancelBreaking();
                return;
            }
            direction = this.getFacingTowardPlayer(this.targetBlock);
        }

        if (this.breakStage == BreakStage.WAITING_FOR_SERVER) {
            return;
        }
        this.selectMiningTool(this.targetBlock);
        if (this.breakStage == BreakStage.IDLE || !this.targetBlock.equals(this.breakingBlock)) {
            this.breakingBlock = this.targetBlock.immutable();
            this.breakingDirection = direction;
            this.breakProgress = 0.0f;
            PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    this.breakingBlock, this.breakingDirection));
            this.breakStage = BreakStage.BREAKING;
            return;
        }

        BlockState state = mc.level.getBlockState(this.breakingBlock);
        if (state.isAir()) {
            this.clearBreakingState();
            return;
        }
        this.breakProgress = Math.min(1.0f,
                this.breakProgress + state.getDestroyProgress(mc.player, mc.level, this.breakingBlock));
        if (this.breakProgress >= 1.0f) {
            PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    this.breakingBlock, this.breakingDirection));
            this.breakStage = BreakStage.WAITING_FOR_SERVER;
            this.serverWaitTicks = 0;
        }
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        this.progressScreenPosition = null;
        if (mc.player == null || mc.level == null || this.targetBlock == null || !this.isInRange(this.targetBlock)) {
            return;
        }

        if (this.renderBreakBlock.getValue()) {
            RenderUtil.drawOutlineBox(new net.minecraft.world.phys.AABB(this.targetBlock), event.poseStack(),
                    new Color(255, 70, 70, 220), 0.9f);
        }
        if (this.renderBreakProgress.getValue() && this.breakingBlock != null
                && this.breakingBlock.equals(this.targetBlock) && this.breakStage != BreakStage.IDLE) {
            Vector2f projected = ProjectionUtil.project(
                    this.targetBlock.getX() + 0.5,
                    this.targetBlock.getY() + 0.5,
                    this.targetBlock.getZ() + 0.5,
                    event.partialTick());
            if (projected != null) {
                this.progressScreenPosition = projected;
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.renderBreakProgress.getValue() || this.progressScreenPosition == null) {
            return;
        }
        float progress = this.breakProgress;
        String text = Math.round(progress * 100.0f) + "%";
        int x = Math.round(this.progressScreenPosition.x - mc.font.width(text) / 2.0f);
        int y = Math.round(this.progressScreenPosition.y - mc.font.lineHeight / 2.0f);
        event.guiGraphics().drawString(mc.font, text, x, y, Color.WHITE.getRGB(), true);
    }

    private BlockPos findBed() {
        int radius = (int) Math.ceil(this.range.getValue().doubleValue());
        BlockPos origin = mc.player.blockPosition();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        double maxDistance = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
        return BlockPos.betweenClosedStream(
                        origin.offset(-radius, -radius, -radius),
                        origin.offset(radius, radius, radius))
                .map(BlockPos::immutable)
                .filter(pos -> this.isBed(pos) && Vec3.atCenterOf(pos).distanceToSqr(eye) <= maxDistance)
                .min(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)))
                .orElse(null);
    }

    private BlockPos findBedDefense(BlockPos bed) {
        Vec3 eye = mc.player.getEyePosition(1.0f);
        LinkedHashSet<BlockPos> defenseBlocks = new LinkedHashSet<>();
        Direction[] exposedFaces = {
                Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (BlockPos bedPart : this.getBedParts(bed)) {
            for (Direction direction : exposedFaces) {
                BlockPos adjacent = bedPart.relative(direction).immutable();
                if (mc.level.isEmptyBlock(adjacent)) {
                    return null;
                }
                if (!this.isBed(adjacent) && this.isInRange(adjacent) && this.isBreakable(adjacent)) {
                    defenseBlocks.add(adjacent);
                }
            }
        }
        return defenseBlocks.stream()
                .min(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)))
                .orElse(null);
    }

    private List<BlockPos> getBedParts(BlockPos bed) {
        List<BlockPos> parts = new ArrayList<>(2);
        parts.add(bed.immutable());
        BlockState state = mc.level.getBlockState(bed);
        if (!state.is(BlockTags.BEDS)
                || !state.hasProperty(BedBlock.PART)
                || !state.hasProperty(BedBlock.FACING)) {
            return parts;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BedPart part = state.getValue(BedBlock.PART);
        BlockPos other = bed.relative(part == BedPart.HEAD ? facing.getOpposite() : facing).immutable();
        if (this.isBed(other)) {
            parts.add(other);
        }
        return parts;
    }

    private boolean isBed(BlockPos pos) {
        return mc.level.getBlockState(pos).is(BlockTags.BEDS);
    }

    private boolean isBreakable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(mc.level, pos) >= 0.0f;
    }

    private boolean isInRange(BlockPos pos) {
        return Vec3.atCenterOf(pos).distanceToSqr(mc.player.getEyePosition(1.0f))
                <= this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
    }

    private Direction getFacingTowardPlayer(BlockPos pos) {
        Vec3 towardPlayer = mc.player.getEyePosition(1.0f).subtract(Vec3.atCenterOf(pos));
        return Direction.getApproximateNearest(towardPlayer);
    }

    private void reset() {
        this.cancelBreaking();
        this.restoreMiningSlot();
        this.bedPosition = null;
        this.targetBlock = null;
        this.targetRotation = null;
        this.progressScreenPosition = null;
    }

    private void cancelBreaking() {
        if (this.breakStage == BreakStage.BREAKING && this.breakingBlock != null
                && mc.player != null && mc.player.connection != null) {
            PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    this.breakingBlock, this.breakingDirection));
        }
        this.clearBreakingState();
    }

    private void clearBreakingState() {
        this.breakingBlock = null;
        this.breakingDirection = Direction.UP;
        this.breakStage = BreakStage.IDLE;
        this.breakProgress = 0.0f;
        this.serverWaitTicks = 0;
    }

    private void selectMiningTool(BlockPos pos) {
        int bestSlot = this.findBestTool(pos);
        if (bestSlot == -1 || bestSlot == mc.player.getInventory().getSelectedSlot()) {
            return;
        }
        if (this.previousSlot == -1) {
            this.previousSlot = mc.player.getInventory().getSelectedSlot();
        }
        mc.player.getInventory().setSelectedSlot(bestSlot);
        PlayerUtil.sendCarriedItem();
    }

    private int findBestTool(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            if (speed > 1.0f) {
                int efficiency = stack.getEnchantmentLevel(
                        mc.level.registryAccess().holderOrThrow(Enchantments.EFFICIENCY));
                if (efficiency > 0) {
                    speed += efficiency * efficiency + 1.0f;
                }
            }
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private void restoreMiningSlot() {
        if (this.previousSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.previousSlot);
            if (mc.gameMode != null) {
                PlayerUtil.sendCarriedItem();
            }
        }
        this.previousSlot = -1;
    }

}

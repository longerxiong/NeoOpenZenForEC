package shit.zen.modules.impl.player;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.*;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.Rotation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChestAura extends Module {
    public static ChestAura INSTANCE;

    private final Set<BlockPos> opened = new HashSet<>();
    private final Set<BlockEntity> blockEntities = new HashSet<>();

    private final NumberSetting range = new NumberSetting("Range", 4, 1, 6, .1);
    @Getter
    private final BooleanSetting movefix = new BooleanSetting("Movement Fix", true);
    private final BooleanSetting swing = new BooleanSetting("Swing", true);
    private final Timer openTimer = new Timer();

    private boolean canRotation;
    @Getter
    private Rotation rotations;
    private BlockPos targetPos;

    public ChestAura() {
        super("ChestAura", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.opened.clear();
        this.canRotation = false;
        this.rotations = null;
        this.targetPos = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.opened.clear();
        this.canRotation = false;
        this.rotations = null;
        this.targetPos = null;
        super.onDisable();
    }

    @EventTarget
    public void onWorld(WorldChangeEvent event) {
        this.opened.clear();
        this.blockEntities.clear();
        this.canRotation = false;
        this.rotations = null;
        this.targetPos = null;
    }

    @EventTarget
    public void onPacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundBlockEventPacket wrapper) {
            mc.execute(() -> {
                if (wrapper.getBlock() instanceof ChestBlock && wrapper.getB0() == 1 && wrapper.getB1() == 1) {
                    this.opened.add(wrapper.getPos());
                }
            });
        }
    }

    public void onRotationApplied(){
        if (this.targetPos == null || mc.level == null || mc.player == null) return;
        if (!(mc.level.getBlockEntity(this.targetPos) instanceof ChestBlockEntity)) return;
        if (this.opened.contains(this.targetPos)) return;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 nearestPoint = RotationUtil.closestPoint(mc.player.getEyePosition(),getChestBox(this.targetPos));
        if (nearestPoint == null) return;

        Direction facing = getFacingDirection(nearestPoint, eyePos);
        BlockHitResult hitResult = new BlockHitResult(nearestPoint, facing, this.targetPos, false);

        if (mc.gameMode != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            if (swing.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                PacketUtil.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            this.opened.add(this.targetPos);
            this.openTimer.reset();
        }
        this.rotations = null;
        this.targetPos = null;
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            this.blockEntities.clear();
            return;
        }

        Set<BlockEntity> loadedBlockEntities = new HashSet<>();
        for (LevelChunk chunk : getLoadedChunks()) {
            loadedBlockEntities.addAll(chunk.getBlockEntities().values());
        }

        this.blockEntities.clear();
        this.blockEntities.addAll(loadedBlockEntities);
        this.opened.removeIf(pos -> !(mc.level.getBlockState(pos).getBlock() instanceof ChestBlock));

        // 寻找最近未打开箱子作为目标
        if (!this.openTimer.hasPassed(250)) return;

        Vec3 eyePos = mc.player.getEyePosition();
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        BlockPos bestPos = null;
        Vec3 bestPoint = null;
        double bestDistSq = Double.MAX_VALUE;

        for (BlockEntity blockEntity : this.blockEntities) {
            if (!(blockEntity instanceof ChestBlockEntity)) continue;

            BlockPos pos = blockEntity.getBlockPos();
            if (this.opened.contains(pos)) continue;

            AABB chestBox = getChestBox(pos);
            Vec3 nearestPoint = RotationUtil.closestPoint(mc.player.getEyePosition(),chestBox);
            if (nearestPoint == null) continue;

            double distSq = eyePos.distanceToSqr(nearestPoint);
            if (distSq > rangeSq) continue;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestPos = pos;
                bestPoint = nearestPoint;
            }
        }

        if (bestPos != null && bestPoint != null) {
            this.targetPos = bestPos;
            this.canRotation = true;
            // 计算旋转
            this.rotations = RotationUtil.rotationFromVec(bestPoint);
        } else {
            this.canRotation = false;
            this.targetPos = null;
        }
    }



    @EventTarget
    public void onLivingUpdate(GameTickEvent e) {
        if (mc.player == null || mc.level == null || targetPos == null) return;
        // 持续更新旋转到目标点
        this.rotations = RotationUtil.rotationFromVec(getChestBox(targetPos).getCenter());
    }

    public boolean isCanRotation() {
        return canRotation && rotations != null;
    }

    private Direction getFacingDirection(Vec3 hitVec, Vec3 eyePos) {
        double dx = hitVec.x - eyePos.x;
        double dy = hitVec.y - eyePos.y;
        double dz = hitVec.z - eyePos.z;

        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        double absDz = Math.abs(dz);

        if (absDy > absDx && absDy > absDz) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        } else if (absDx > absDz) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private AABB getChestBox(BlockPos pos) {
        double minX = pos.getX() + 0.06;
        double minZ = pos.getZ() + 0.06;
        double maxX = pos.getX() + 0.94;
        double maxZ = pos.getZ() + 0.94;
        BlockState state = mc.level.getBlockState(pos);

        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
            if (otherPos.getX() < pos.getX()) minX = pos.getX();
            else if (otherPos.getX() > pos.getX()) maxX = pos.getX() + 1;
            else if (otherPos.getZ() < pos.getZ()) minZ = pos.getZ();
            else if (otherPos.getZ() > pos.getZ()) maxZ = pos.getZ() + 1;
        }

        return new AABB(minX, pos.getY(), minZ, maxX, pos.getY() + 0.875, maxZ);
    }

    public static List<LevelChunk> getLoadedChunks() {
        List<LevelChunk> chunks = new ArrayList<>();
        if (mc.level == null || mc.player == null) return chunks;

        int viewDist = mc.options.renderDistance().get();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        for (int x = -viewDist; x <= viewDist; x++) {
            for (int z = -viewDist; z <= viewDist; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(playerChunkX + x, playerChunkZ + z);
                if (chunk != null) chunks.add(chunk);
            }
        }
        return chunks;
    }
}

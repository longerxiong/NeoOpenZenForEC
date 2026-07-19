package shit.zen.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.ChunkUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.event.EventTarget;

public class ChestESP
extends Module {
    private static final float[] chestColor;
    private static final float[] openedChestColor;
    private final List<BlockPos> openedChestPositions = new CopyOnWriteArrayList<>();
    private final List<AABB> renderBoundingBoxes = new CopyOnWriteArrayList<>();
    private static final String MODULE_NAME;

    public ChestESP() {
        super(MODULE_NAME, Category.RENDER);
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent worldChangeEvent) {
        this.openedChestPositions.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        ClientboundBlockEventPacket clientboundBlockEventPacket;
        Packet<?> packet;
        if (packetEvent.isIncoming() && (packet = packetEvent.getPacket()) instanceof ClientboundBlockEventPacket && ((clientboundBlockEventPacket = (ClientboundBlockEventPacket)packet).getBlock() == Blocks.CHEST || clientboundBlockEventPacket.getBlock() == Blocks.TRAPPED_CHEST) && clientboundBlockEventPacket.getB0() == 1 && clientboundBlockEventPacket.getB1() == 1) {
            this.openedChestPositions.add(clientboundBlockEventPacket.getPos());
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPost()) {
            ArrayList<BlockEntity> arrayList = ChunkUtil.getLoadedBlockEntities().collect(Collectors.toCollection(ArrayList::new));
            this.renderBoundingBoxes.clear();
            for (BlockEntity blockEntity : arrayList) {
                ChestBlockEntity chestBlockEntity;
                AABB aABB;
                if (!(blockEntity instanceof ChestBlockEntity) || (aABB = this.getChestAabb(chestBlockEntity = (ChestBlockEntity)blockEntity)) == null) continue;
                this.renderBoundingBoxes.add(aABB);
            }
        }
    }

    private AABB getChestAabb(ChestBlockEntity chestBlockEntity) {
        BlockPos blockPos;
        BlockState blockState = chestBlockEntity.getBlockState();
        if (!blockState.hasProperty((Property)ChestBlock.TYPE)) {
            return null;
        }
        ChestType chestType = (ChestType)blockState.getValue((Property)ChestBlock.TYPE);
        if (chestType == ChestType.LEFT) {
            return null;
        }
        BlockPos blockPos2 = chestBlockEntity.getBlockPos();
        AABB aABB = BlockUtil.getBoundingBox(blockPos2);
        if (chestType != ChestType.SINGLE && BlockUtil.canBeClicked(blockPos = blockPos2.relative(ChestBlock.getConnectedDirection(blockState)))) {
            AABB aABB2 = BlockUtil.getBoundingBox(blockPos);
            aABB = aABB.minmax(aABB2);
        }
        return aABB;
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        PoseStack poseStack = renderEvent.poseStack();
        poseStack.pushPose();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (AABB aABB : this.renderBoundingBoxes) {
            BlockPos blockPos = BlockPos.containing(aABB.minX, aABB.minY, aABB.minZ);
            float[] fArray = this.openedChestPositions.contains(blockPos) ? openedChestColor : chestColor;
            RenderUtil.drawSolidBox(aABB, poseStack, new Color(fArray[0], fArray[1], fArray[2]), 0.25f);
        }
        poseStack.popPose();
    }

    static {
        MODULE_NAME = "ChestESP";
        chestColor = new float[]{0.0f, 1.0f, 0.0f};
        openedChestColor = new float[]{1.0f, 0.0f, 0.0f};
    }
}

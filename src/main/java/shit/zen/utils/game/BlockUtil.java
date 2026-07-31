package shit.zen.utils.game;

import java.util.List;
import lombok.Generated;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FletchingTableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import shit.zen.ClientBase;

public final class BlockUtil
extends ClientBase {
    public static final List<Block> blacklist = new java.util.ArrayList<>();

    public static boolean isEmpty(BlockPos blockPos) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        Block block = mc.level.getBlockState(blockPos).getBlock();
        return block instanceof AirBlock;
    }

    public static boolean canBeClicked(BlockPos blockPos) {
        return BlockUtil.getVoxelShape(blockPos) != Shapes.empty();
    }

    public static AABB getBoundingBox(BlockPos blockPos) {
        return BlockUtil.getVoxelShape(blockPos).bounds().move(blockPos);
    }

    private static VoxelShape getVoxelShape(BlockPos blockPos) {
        return BlockUtil.getBlockState(blockPos).getShape(mc.level, blockPos);
    }

    public static BlockState getBlockState(BlockPos blockPos) {
        if (mc.level == null) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return mc.level.getBlockState(blockPos);
    }

    public static boolean isSolid(BlockState blockState) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return BlockUtil.isSolid(blockState.getBlock());
    }

    public static boolean isSolid(BlockPos blockPos) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return BlockUtil.isSolid(mc.level.getBlockState(blockPos).getBlock());
    }

    public static boolean isSolid(Block block) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return !(block instanceof LiquidBlock) && !(block instanceof AirBlock) && !(block instanceof ChestBlock) && !(block instanceof FurnaceBlock) && !(block instanceof CraftingTableBlock) && !(block instanceof LadderBlock) && !(block instanceof TntBlock);
    }

    public static Block getBlock(BlockPos blockPos) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        return mc.level.getBlockState(blockPos).getBlock();
    }

    public static BlockPos getPos(double x, double y, double z) {
        return new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
    }

    public static BlockPos getOffsetPos(BlockPos blockPos, double dx, double dy, double dz) {
        return new BlockPos((int)Math.floor((double)blockPos.getX() + dx), (int)Math.floor((double)blockPos.getY() + dy), (int)Math.floor((double)blockPos.getZ() + dz));
    }

    public static boolean isPlaceable(ItemStack itemStack) {
        return itemStack != null && itemStack.getCount() > 1 && isSafeBridgeBlock(itemStack);
    }

    public static boolean isSafeBridgeBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !(itemStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!ItemUtil.isUsable(itemStack)) {
            return false;
        }
        String displayName = itemStack.getDisplayName().getString();
        if (displayName.contains("Click") || displayName.contains("点击")) {
            return false;
        }

        Block block = blockItem.getBlock();
        if (blacklist.contains(block)
                || block instanceof BaseEntityBlock
                || block instanceof FallingBlock
                || block instanceof TntBlock
                || block instanceof CraftingTableBlock
                || block instanceof CartographyTableBlock
                || block instanceof FletchingTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof LoomBlock
                || block instanceof GrindstoneBlock
                || block instanceof FlowerBlock
                || block instanceof BushBlock
                || block instanceof FungusBlock
                || block instanceof CropBlock
                || block instanceof SlabBlock
                || block instanceof WebBlock) {
            return false;
        }
        return block.defaultBlockState().isCollisionShapeFullBlock(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    @Generated
    private BlockUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

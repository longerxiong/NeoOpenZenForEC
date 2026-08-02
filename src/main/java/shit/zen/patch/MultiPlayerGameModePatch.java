package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import shit.zen.modules.impl.player.ChestStealer;

@Patch(MultiPlayerGameMode.class)
public final class MultiPlayerGameModePatch {
    private MultiPlayerGameModePatch() {
    }

    @Inject(
            method = "useItemOn",
            desc = "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(At.Type.TAIL)
    )
    public static void onUseItemOn(MultiPlayerGameMode gameMode, LocalPlayer player,
                                   InteractionHand hand, BlockHitResult hitResult,
                                   CallbackInfo callbackInfo) {
        if (callbackInfo.result instanceof InteractionResult result && result.consumesAction()) {
            ChestStealer.captureContainerInteraction(hitResult.getBlockPos());
        }
    }
}

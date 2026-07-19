package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.RotationAnimationEvent;

@Patch(LivingEntityRenderer.class)
public class LivingEntityRendererPatch {
    @Inject(
            method = "extractRenderState",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(At.Type.TAIL)
    )
    public static void onExtractRenderState(
            LivingEntityRenderer<?, ?, ?> renderer,
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTick,
            CallbackInfo callbackInfo) {
        if (!ZenClient.isReady() || entity != ClientBase.mc.player) {
            return;
        }
        RotationAnimationEvent event = new RotationAnimationEvent(
                entity.yHeadRot, entity.yHeadRotO, entity.getXRot(), entity.xRotO);
        ZenClient.getInstance().getEventBus().call(event);
        float headYaw = Mth.rotLerp(partialTick, event.getLastYaw(), event.getYaw());
        state.yRot = Mth.wrapDegrees(headYaw - state.bodyRot);
        state.xRot = Mth.lerp(partialTick, event.getLastPitch(), event.getPitch());
    }
}

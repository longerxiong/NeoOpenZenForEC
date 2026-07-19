package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.NameTags;

@Patch(EntityRenderer.class)
public class EntityRendererPatch {
    @Inject(
            method = "renderNameTag",
            desc = "(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderNameTag(EntityRenderer<?, ?> renderer, EntityRenderState state, Component component, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (state instanceof LivingEntityRenderState && ZenClient.isReady() && NameTags.INSTANCE != null && NameTags.INSTANCE.isEnabled()) {
            callbackInfo.cancel();
        }
    }
}

package shit.zen.event.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import shit.zen.event.Event;

@Getter
public class RenderEntityEvent
extends Event {
    private final Entity entity;
    private final PoseStack poseStack;
    private final MultiBufferSource bufferSource;
    private final float partialTick;
    private final int packedLight;
    private boolean cancelled;
    private final EntityRenderer<?, ?> entityRenderer;

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public RenderEntityEvent(EntityRenderer<?, ?> entityRenderer, Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight, boolean cancelled) {
        this.entityRenderer = entityRenderer;
        this.entity = entity;
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.partialTick = partialTick;
        this.packedLight = packedLight;
        this.cancelled = cancelled;
    }

    public static class Pre extends RenderEntityEvent {
        public Pre(EntityRenderer<?, ?> renderer, Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight) {
            super(renderer, entity, poseStack, bufferSource, partialTick, packedLight, false);
        }
    }

    public static class Post extends RenderEntityEvent {
        public Post(EntityRenderer<?, ?> renderer, Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight) {
            super(renderer, entity, poseStack, bufferSource, partialTick, packedLight, false);
        }
    }
}
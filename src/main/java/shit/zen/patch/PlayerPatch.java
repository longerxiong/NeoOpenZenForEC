package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.EntityRemoveEvent;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.exploit.RemoteStore;
import shit.zen.modules.impl.movement.KeepSprint;

@Patch(Player.class)
public class PlayerPatch {
    @Inject(method = "closeContainer", desc = "()V", at = @At(At.Type.HEAD))
    public static void onCloseContainer(Player player, CallbackInfo callbackInfo) {
        if (RemoteStore.shouldKeepContainerOpen(player)) {
            callbackInfo.cancel();
        }
    }

    @WrapInvoke(
            method = "die",
            desc = "(Lnet/minecraft/world/damagesource/DamageSource;)V",
            target = "net/minecraft/world/entity/Entity/getYRot",
            targetDesc = "()F"
    )
    public static float onDieGetYRot(Player player, DamageSource source, Invocation<Player, Float> original) throws Exception {
        return ClientBase.yaw;
    }

    @WrapInvoke(method = "attack", desc = "(Lnet/minecraft/world/entity/Entity;)V", target = "net/minecraft/world/entity/Entity/getYRot", targetDesc = "()F")
    public static float onAttackGetYRot(Player player, Entity target, Invocation<Player, Float> original) throws Exception {
        return ClientBase.yaw;
    }

    @WrapInvoke(
            method = "attack",
            desc = "(Lnet/minecraft/world/entity/Entity;)V",
            target = "net/minecraft/world/entity/player/Player/setDeltaMovement",
            targetDesc = "(Lnet/minecraft/world/phys/Vec3;)V"
    )
    public static void onAttackSetDeltaMovement(Player player, Entity target,
                                                 Invocation<Player, Void> original) throws Exception {
        if (!shouldKeepSprint(player)) {
            original.call();
        }
    }

    @WrapInvoke(
            method = "attack",
            desc = "(Lnet/minecraft/world/entity/Entity;)V",
            target = "net/minecraft/world/entity/player/Player/setSprinting",
            targetDesc = "(Z)V"
    )
    public static void onAttackSetSprinting(Player player, Entity target,
                                            Invocation<Player, Void> original) throws Exception {
        if (!shouldKeepSprint(player)) {
            original.call();
        }
    }

    private static boolean shouldKeepSprint(Player player) {
        if (!ZenClient.isReady() || player != ClientBase.mc.player) {
            return false;
        }
        boolean standaloneEnabled = KeepSprint.INSTANCE != null && KeepSprint.INSTANCE.isEnabled();
        boolean auraEnabled = KillAura.INSTANCE != null
                && KillAura.INSTANCE.isEnabled()
                && KillAura.INSTANCE.keepSprint.getValue();
        return standaloneEnabled || auraEnabled;
    }

    @Inject(method = "attack", desc = "(Lnet/minecraft/world/entity/Entity;)V", at = @At(At.Type.HEAD))
    public static void onAttackPre(Player player, Entity target, CallbackInfo callbackInfo) {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new EntityRemoveEvent(false, target));
        }
    }

    @Inject(method = "attack", desc = "(Lnet/minecraft/world/entity/Entity;)V", at = @At(At.Type.TAIL))
    public static void onAttackPost(Player player, Entity target, CallbackInfo callbackInfo) {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new EntityRemoveEvent(true, target));
        }
    }


}

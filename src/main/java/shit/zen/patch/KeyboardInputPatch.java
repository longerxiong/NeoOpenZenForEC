package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import shit.zen.ZenClient;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(KeyboardInput.class)
public class KeyboardInputPatch extends ClientInput {

    @Inject(method = "tick", desc = "()V", at = @At(At.Type.TAIL))
    public static void onTick(KeyboardInput input, CallbackInfo callbackInfo) {
        Input keys = input.keyPresses;
        float forward = keys.forward() == keys.backward() ? 0.0f : (keys.forward() ? 1.0f : -1.0f);
        float strafe = keys.left() == keys.right() ? 0.0f : (keys.left() ? 1.0f : -1.0f);
        StrafeEvent event = new StrafeEvent(forward, strafe, keys.jump());
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        double sneakFactor = 0.3;
        float newForward = event.getForward();
        float newStrafe = event.getStrafe();
        boolean newJumping = event.isSprinting();
        if (keys.shift()) {
            newStrafe = (float) (newStrafe * sneakFactor);
            newForward = (float) (newForward * sneakFactor);
        }
        input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), newJumping, keys.shift(), keys.sprint());
        ReflectionUtil.setFieldValue(input, new Vec2(newStrafe, newForward).normalized(), "moveVector");
    }
}

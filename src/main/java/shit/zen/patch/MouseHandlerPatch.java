package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.MouseHandler;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.modules.impl.player.ChestStealer;

@Patch(MouseHandler.class)
public final class MouseHandlerPatch extends ClientBase {
    private MouseHandlerPatch() {
    }

    @Inject(method = "releaseMouse", desc = "()V", at = @At(At.Type.HEAD))
    public static void onReleaseMouse(MouseHandler mouseHandler, CallbackInfo callbackInfo) {
        if (ZenClient.isReady() && ZenClient.getInstance().getModuleManager()!= null
                && ZenClient.getInstance().getModuleManager().getModule(ChestStealer.class) != null
                && ChestStealer.isSilentContainerScreen(mc.screen)) {
            callbackInfo.cancel();
        }
    }
}

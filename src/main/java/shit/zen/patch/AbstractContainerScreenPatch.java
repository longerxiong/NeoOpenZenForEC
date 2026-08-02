package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import shit.zen.ClientBase;
import shit.zen.modules.impl.player.ChestStealer;

@Patch(AbstractContainerScreen.class)
public final class AbstractContainerScreenPatch extends ClientBase {
    private AbstractContainerScreenPatch() {
    }

    @Inject(method = "mouseClicked", desc = "(DDI)Z", at = @At(At.Type.HEAD))
    public static void onMouseClicked(AbstractContainerScreen<?> screen, double mouseX,
                                      double mouseY, int button, CallbackInfo callbackInfo) {
        blockMouseInput(screen, callbackInfo);
    }

    @Inject(method = "mouseDragged", desc = "(DDIDD)Z", at = @At(At.Type.HEAD))
    public static void onMouseDragged(AbstractContainerScreen<?> screen, double mouseX,
                                      double mouseY, int button, double dragX, double dragY,
                                      CallbackInfo callbackInfo) {
        blockMouseInput(screen, callbackInfo);
    }

    @Inject(method = "mouseReleased", desc = "(DDI)Z", at = @At(At.Type.HEAD))
    public static void onMouseReleased(AbstractContainerScreen<?> screen, double mouseX,
                                       double mouseY, int button, CallbackInfo callbackInfo) {
        blockMouseInput(screen, callbackInfo);
    }

    private static void blockMouseInput(AbstractContainerScreen<?> screen, CallbackInfo callbackInfo) {
        if (ChestStealer.isSilentContainerScreen(mc.screen) && mc.screen == screen) {
            callbackInfo.result = Boolean.TRUE;
            callbackInfo.cancel();
        }
    }
}

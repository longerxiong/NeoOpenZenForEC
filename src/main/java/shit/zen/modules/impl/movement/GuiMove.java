package shit.zen.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Input;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.ChestStealer;

public class GuiMove
extends Module {
    public static GuiMove INSTANCE;

    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static Input resolveGuiInput(Input original) {
        boolean guiMoveEnabled = INSTANCE != null && INSTANCE.isEnabled();
        boolean silentChestOpen = ChestStealer.isSilentContainerScreen(mc.screen);
        if ((!guiMoveEnabled && !silentChestOpen)
                || mc.player == null
                || mc.screen == null
                || mc.screen instanceof ChatScreen) {
            return original;
        }

        boolean forward = isPhysicallyDown(mc.options.keyUp);
        boolean backward = isPhysicallyDown(mc.options.keyDown);
        boolean left = isPhysicallyDown(mc.options.keyLeft);
        boolean right = isPhysicallyDown(mc.options.keyRight);
        boolean jump = isPhysicallyDown(mc.options.keyJump);
        boolean shift = isPhysicallyDown(mc.options.keyShift);
        boolean sprint = isPhysicallyDown(mc.options.keySprint);

        // Opening a screen calls KeyMapping.releaseAll(). Keep the public key
        // states in sync as well as KeyboardInput so movement-dependent modules
        // do not observe a false key release while GuiMove is active.
        mc.options.keyUp.setDown(forward);
        mc.options.keyDown.setDown(backward);
        mc.options.keyLeft.setDown(left);
        mc.options.keyRight.setDown(right);
        mc.options.keyJump.setDown(jump);
        mc.options.keyShift.setDown(shift);
        mc.options.keySprint.setDown(sprint);

        return new Input(
                forward,
                backward,
                left,
                right,
                jump,
                shift,
                sprint);
    }

    private static boolean isPhysicallyDown(KeyMapping keyMapping) {
        InputConstants.Key key = keyMapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }
}

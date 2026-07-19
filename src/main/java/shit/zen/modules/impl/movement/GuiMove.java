package shit.zen.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Input;
import shit.zen.modules.Category;
import shit.zen.modules.Module;

public class GuiMove
extends Module {
    public static GuiMove INSTANCE;

    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static Input resolveGuiInput(Input original) {
        if (INSTANCE == null
                || !INSTANCE.isEnabled()
                || mc.player == null
                || mc.screen == null
                || mc.screen instanceof ChatScreen) {
            return original;
        }

        return new Input(
                isPhysicallyDown(mc.options.keyUp),
                isPhysicallyDown(mc.options.keyDown),
                isPhysicallyDown(mc.options.keyLeft),
                isPhysicallyDown(mc.options.keyRight),
                isPhysicallyDown(mc.options.keyJump),
                isPhysicallyDown(mc.options.keyShift),
                isPhysicallyDown(mc.options.keySprint));
    }

    private static boolean isPhysicallyDown(KeyMapping keyMapping) {
        InputConstants.Key key = keyMapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }
}

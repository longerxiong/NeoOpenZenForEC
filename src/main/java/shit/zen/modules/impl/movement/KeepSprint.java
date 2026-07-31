package shit.zen.modules.impl.movement;

import shit.zen.modules.Category;
import shit.zen.modules.Module;

/**
 * Prevents the player from slowing down and losing sprint when attacking entities.
 * <p>
 * PlayerPatch uses this module as a switch and skips the two vanilla operations
 * that reduce horizontal velocity and stop sprinting after a knockback attack.
 */
public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    public KeepSprint() {
        super("KeepSprint", Category.MOVEMENT);
        INSTANCE = this;
    }
}

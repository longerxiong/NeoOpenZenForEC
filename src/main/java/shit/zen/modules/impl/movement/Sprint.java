package shit.zen.modules.impl.movement;

import shit.zen.event.impl.JumpEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.utils.game.MovementUtil;

public class Sprint
extends Module {
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

//    public final BooleanSetting full = new BooleanSetting("Full", false);

    @EventTarget
    public void onStrafe(StrafeEvent event){
        mc.options.keySprint.setDown(true);
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (/*full.getValue() &&*/ mc.player != null) {
            // Omnidirectional: set jump yaw to actual input direction
            event.setYaw((float) Math.toDegrees(MovementUtil.getMovementYaw()));
        }
    }
}
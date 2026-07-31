package shit.zen.modules.impl.movement;

import shit.zen.event.impl.JumpEvent;
import shit.zen.event.impl.JumpMarkerEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.utils.game.MovementUtil;

public class Sprint
extends Module {
    public static Sprint INSTANCE;

    public final BooleanSetting full = new BooleanSetting("Full", false);

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        INSTANCE = this;
        this.setEnabled(true);
    }

    public static boolean isFullEnabled() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.full.getValue();
    }

    @EventTarget
    public void onStrafe(StrafeEvent event){
        mc.options.keySprint.setDown(true);
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (this.full.getValue() && mc.player != null) {
            event.setYaw((float) Math.toDegrees(MovementUtil.getMovementYaw()));
        }
    }

    @EventTarget
    public void onJumpBoost(JumpMarkerEvent event) {
        if (this.full.getValue() && mc.player != null && MovementUtil.isInputActive()) {
            // Align vanilla's 0.2 sprint-jump boost with the actual input direction.
            event.setYaw((float) Math.toDegrees(MovementUtil.getMovementYaw()));
        }
    }
}

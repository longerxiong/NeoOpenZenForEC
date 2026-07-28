package shit.zen.modules.impl.movement.speed.impl;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.impl.movement.SpeedModule;
import shit.zen.modules.impl.movement.speed.SpeedMode;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.MovementUtil;

public class SpeedOnGround extends SpeedMode {
    public SpeedOnGround() {
        super("OnGround");
    }

    private final NumberSetting speed = new NumberSetting("Speed", 1, 1, 5, 0.1f, ()-> SpeedModule.is(this));

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        if (mc.player == null || mc.level == null) return;
        if (MovementUtil.isMoving() && mc.player.onGround()) {
            MovementUtil.strafeForward(speed.getValue().doubleValue() / 12);
        }
    }
}

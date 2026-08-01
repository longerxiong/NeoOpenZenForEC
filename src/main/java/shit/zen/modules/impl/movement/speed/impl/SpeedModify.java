package shit.zen.modules.impl.movement.speed.impl;

import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.movement.Speed;
import shit.zen.modules.impl.movement.speed.SpeedMode;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.game.MovementUtil;

public class SpeedModify extends SpeedMode {
    public SpeedModify() {
        super("Modify");
    }

    private final NumberSetting speed = new NumberSetting("Speed", 1, 1, 5, 0.1f, ()-> Speed.is(this));
    private final Scaffold scaffold = ZenClient.getInstance().getModuleManager().getModule(Scaffold.class);

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        if (mc.player == null || mc.level == null) return;
        if (MovementUtil.isMoving()) {
            if(scaffold.isEnabled()){
                if(mc.player.onGround()){
                    MovementUtil.strafeForward(speed.getValue().doubleValue() / 12);
                }
                return;
            }
            MovementUtil.strafeForward(speed.getValue().doubleValue() / 12);
        }
    }
}

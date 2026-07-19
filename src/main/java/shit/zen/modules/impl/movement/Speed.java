package shit.zen.modules.impl.movement;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.game.MovementUtil;

public class Speed extends Module {
    public Speed() {
        super("Speed", Category.MOVEMENT);
    }

    private final NumberSetting vanillaSpeed = new NumberSetting("Speed", 1, 1, 5, 0.1f);

    @EventTarget
    public void onMotion(MotionEvent event){
        if(MovementUtil.isMoving() && mc.player.onGround()){
            MovementUtil.strafeForward(vanillaSpeed.getValue().doubleValue()/12);
        }
    }

}

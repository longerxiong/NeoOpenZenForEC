package shit.zen.modules.impl.movement;

import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.speed.SpeedMode;
import shit.zen.modules.impl.movement.speed.impl.SpeedModify;
import shit.zen.modules.impl.movement.speed.impl.SpeedOnGround;
import shit.zen.modules.settings.impl.ModeSetting;

import java.util.List;

public class Speed extends Module {
    private final List<SpeedMode> modes = List.of(
            new SpeedOnGround(),
            new SpeedModify()
    );
    public final ModeSetting mode;
    private static SpeedMode activeMode;

    public Speed() {
        super("Speed", Category.MOVEMENT);
        mode = new ModeSetting("Mode", getModeNames()).withDefault(modes.get(0).getName());
        activeMode = modes.get(0);
        activeMode.setActive(true);
        modes.forEach(speedMode -> getSettings().addAll(speedMode.getValues()));
    }

    private String[] getModeNames() {
        return modes.stream().map(SpeedMode::getName).toArray(String[]::new);
    }

    @Override
    public void onEnable() {
        activeMode = getActiveMode();
        activeMode.setActive(true);
        ZenClient.getInstance().getEventBus().register(activeMode);
        activeMode.onEnable();
    }

    @Override
    public void onDisable() {
        activeMode.onDisable();
        ZenClient.getInstance().getEventBus().unregister(activeMode);
        activeMode.setActive(false);
    }

    @EventTarget
    public void onUpdate(GameTickEvent event){
        SpeedMode selected = getActiveMode();

        if (activeMode != selected) {
            activeMode.onDisable();
            ZenClient.getInstance().getEventBus().unregister(activeMode);
            activeMode.setActive(false);
            activeMode = selected;
            activeMode.setActive(true);
            ZenClient.getInstance().getEventBus().register(activeMode);
            activeMode.onEnable();
        }
    }

    private SpeedMode getActiveMode(){
        for(SpeedMode speedMode : modes){
            if(mode.is(speedMode.getName())){
                return speedMode;
            }
        }

        return modes.get(0);
    }

    public static boolean is(SpeedMode mode){
        return activeMode == mode;
    }
}

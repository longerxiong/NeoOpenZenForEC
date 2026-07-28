package shit.zen.modules.impl.movement.speed;

import lombok.Getter;
import lombok.Setter;
import shit.zen.ClientBase;
import shit.zen.modules.settings.Setting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
public abstract class SpeedMode extends ClientBase {
    private final String name;
    @Setter
    private boolean active;

    public SpeedMode(String name) {this.name = name;}

    public void onEnable() {}
    public void onDisable() {}

    public Supplier<Boolean> visible() {
        return this::isActive;
    }

    public List<Setting<?>> getValues() {
        List<Setting<?>> values = new ArrayList<>();

        try {
            Class<?> clazz = getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Setting.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object valueObject = field.get(this);
                        if (valueObject != null) values.add((Setting<?>) valueObject);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {
        }

        return values;
    }

}

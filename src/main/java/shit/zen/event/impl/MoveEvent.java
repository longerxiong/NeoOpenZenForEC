package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.MoverType;
import shit.zen.event.Event;

@Getter
@Setter
public class MoveEvent extends Event {
    private final MoverType type;
    private double x, y, z;

    public MoveEvent(MoverType type, double x, double y, double z) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

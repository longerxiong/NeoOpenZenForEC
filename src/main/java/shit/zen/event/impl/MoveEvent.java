package shit.zen.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import shit.zen.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class MoveEvent extends Event {
    private double x, y, z;
}

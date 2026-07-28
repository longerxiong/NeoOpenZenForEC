package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import shit.zen.event.Event;

public class JumpEvent
extends Event {
    @Getter @Setter
    private float yaw;

    @Generated
    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }
}
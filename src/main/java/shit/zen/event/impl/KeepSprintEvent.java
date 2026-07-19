package shit.zen.event.impl;

import shit.zen.event.EventMarker;

/**
 * Fired before and after {@code Player.attack(Entity)} so that KeepSprint
 * can save/restore the player's sprinting state around an attack.
 */
public class KeepSprintEvent implements EventMarker {

    public static class Pre extends KeepSprintEvent {}
    public static class Post extends KeepSprintEvent {}
}

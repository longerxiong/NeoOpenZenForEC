package shit.zen.modules.impl.movement;

import net.minecraft.client.KeyMapping;
import shit.zen.event.impl.KeepSprintEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.event.EventTarget;

/**
 * Prevents the player from slowing down and losing sprint when attacking entities.
 * <p>
 * Vanilla Minecraft cancels sprinting (sets {@code setSprinting(false)}) after
 * every attack hit. This module counteracts that by:
 * <ul>
 *   <li>Saving the sprint state before the attack ({@link KeepSprintEvent.Pre}).</li>
 *   <li>Restoring sprinting immediately after the attack ({@link KeepSprintEvent.Post}).</li>
 *   <li>Keeping the sprint key held down on every client tick ({@link SprintEvent}).</li>
 * </ul>
 */
public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    public final BooleanSetting keepSprintKey = new BooleanSetting("Keep Sprint Key", true);

    private boolean wasSprintingBeforeAttack;

    public KeepSprint() {
        super("KeepSprint", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.wasSprintingBeforeAttack = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.wasSprintingBeforeAttack = false;
        super.onDisable();
    }

    /**
     * Called every client tick (via LocalPlayerPatch.onTick).
     * Continuously holds the sprint key so the player keeps sprinting.
     */
    @EventTarget
    public void onSprint(SprintEvent event) {
        if (mc.player == null) return;
        if (this.keepSprintKey.getValue()) {
            mc.options.toggleSprint().set(false);
            KeyMapping.set(mc.options.keySprint.getKey(), true);
        }
    }

    /**
     * Called right before Player.attack() executes.
     * Saves the current sprint state so we can restore it afterwards.
     */
    @EventTarget
    public void onAttackPre(KeepSprintEvent.Pre event) {
        if (mc.player == null) return;
        this.wasSprintingBeforeAttack = mc.player.isSprinting();
    }

    /**
     * Called right after Player.attack() finishes.
     * Restores the sprint state that vanilla cancelled.
     */
    @EventTarget
    public void onAttackPost(KeepSprintEvent.Post event) {
        if (mc.player == null) return;
        // Restore sprinting if we were sprinting before the attack
        // or if the sprint key is being held
        if (this.wasSprintingBeforeAttack || mc.options.keySprint.isDown()) {
            mc.player.setSprinting(true);
        }
        // Also keep the sprint key pressed so the next movement tick re-applies sprint
        if (this.keepSprintKey.getValue()) {
            mc.options.toggleSprint().set(false);
            KeyMapping.set(mc.options.keySprint.getKey(), true);
        }
    }
}

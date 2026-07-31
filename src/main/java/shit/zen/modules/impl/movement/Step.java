package shit.zen.modules.impl.movement;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;

/**
 * Raises the local player's step height while grounded so ordinary blocks can
 * be walked up without jumping.
 */
public class Step extends Module {
    public static Step INSTANCE;

    private AttributeInstance modifiedAttribute;
    private double originalBaseValue;

    public Step() {
        super("Step", Category.MOVEMENT);
        INSTANCE = this;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        AttributeInstance attribute = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (attribute == null) {
            return;
        }

        if (attribute != this.modifiedAttribute) {
            this.restoreAttribute();
            this.modifiedAttribute = attribute;
            this.originalBaseValue = attribute.getBaseValue();
        }

        attribute.setBaseValue(mc.player.onGround() ? 1.0 : this.originalBaseValue);
    }

    @Override
    protected void onDisable() {
        this.restoreAttribute();
        super.onDisable();
    }

    private void restoreAttribute() {
        if (this.modifiedAttribute != null) {
            this.modifiedAttribute.setBaseValue(this.originalBaseValue);
            this.modifiedAttribute = null;
        }
    }
}

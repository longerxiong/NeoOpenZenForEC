package shit.zen.modules.impl.render.esp;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;

import net.minecraft.world.entity.projectile.AbstractThrownPotion;

public class PotionEspColor
extends ClassEspColor {
    public PotionEspColor() {
        super(new HashSet<>(Collections.singleton(AbstractThrownPotion.class)), new Color(255, 66, 249));
    }

    public float getLineWidth() {
        return 0.05f;
    }
}
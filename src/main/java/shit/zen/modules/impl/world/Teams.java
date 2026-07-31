package shit.zen.modules.impl.world;

import java.util.Objects;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.MultiSelectSetting;

public class Teams
extends Module {
    public static Teams instance;
    public static ModeSetting mode;
    private final MultiSelectSetting armorParts = new MultiSelectSetting(
            "Armor Parts", "Helmet", "Chestplate", "Leggings", "Boots")
            .withDefaults("Helmet")
            .withVisibility(() -> mode.is("Armor"));

    public Teams() {
        super("Teams", Category.WORLD);
        instance = this;
    }

    public static boolean isSameTeam(Entity entity) {
        if (instance == null || !instance.isEnabled() || mc.player == null) {
            return false;
        }
        if (entity instanceof Player player) {
            if (mode.is("Color")) {
                Integer n = entity.getTeamColor();
                Integer n2 = mc.player.getTeamColor();
                return n.equals(n2);
            }
            if (mode.is("Armor")) {
                return instance.hasMatchingArmor(player);
            }
            String string = Teams.getTeam(entity);
            String string2 = Teams.getTeam(mc.player);
            return string != null && Objects.equals(string, string2);
        }
        return false;
    }

    private boolean hasMatchingArmor(Player player) {
        boolean selected = false;
        for (ArmorPart part : ArmorPart.values()) {
            if (!this.armorParts.isSelected(part.settingName)) {
                continue;
            }
            selected = true;
            if (!isSameArmor(mc.player.getItemBySlot(part.slot), player.getItemBySlot(part.slot))) {
                return false;
            }
        }
        return selected;
    }

    private static boolean isSameArmor(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty() || first.getItem() != second.getItem()) {
            return false;
        }
        DyedItemColor firstColor = first.get(DataComponents.DYED_COLOR);
        DyedItemColor secondColor = second.get(DataComponents.DYED_COLOR);
        return Objects.equals(firstColor, secondColor);
    }

    public static String getTeam(Entity entity) {
        if (mc.getConnection() == null) {
            return null;
        }
        PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(entity.getUUID());
        if (playerInfo == null) {
            return null;
        }
        if (playerInfo.getTeam() != null) {
            return playerInfo.getTeam().getName();
        }
        return null;
    }

    private enum ArmorPart {
        HELMET("Helmet", EquipmentSlot.HEAD),
        CHESTPLATE("Chestplate", EquipmentSlot.CHEST),
        LEGGINGS("Leggings", EquipmentSlot.LEGS),
        BOOTS("Boots", EquipmentSlot.FEET);

        private final String settingName;
        private final EquipmentSlot slot;

        ArmorPart(String settingName, EquipmentSlot slot) {
            this.settingName = settingName;
            this.slot = slot;
        }
    }

    static {
        mode = new ModeSetting("Mode", "Color", "Scoreboard", "Armor").withDefault("Scoreboard");
    }
}

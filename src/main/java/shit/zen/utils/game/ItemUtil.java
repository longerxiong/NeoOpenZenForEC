package shit.zen.utils.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Generated;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import shit.zen.ClientBase;

public final class ItemUtil
extends ClientBase {
    /**
     * Plain materials and decorative blocks that have no practical SkyWars use.
     * Custom server items using the same vanilla base item are deliberately kept.
     */
    public static boolean isSkyWarsJunk(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item == Items.PHANTOM_MEMBRANE
                || item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT
                || item == Items.LAPIS_LAZULI
                || item == Items.PAPER
                || item == Items.FLINT
                || item == Items.FEATHER
                || item == Items.NETHERITE_SCRAP
                || item == Items.APPLE
                || item == Items.ENCHANTED_BOOK
                || item == Items.SPYGLASS
                || item == Items.DIAMOND
                || item == Items.NETHERITE_INGOT
                || item == Items.GLOWSTONE_DUST) {
            return true;
        }
        if (itemStack.has(DataComponents.CUSTOM_NAME)
                || itemStack.has(DataComponents.CUSTOM_DATA)
                || itemStack.has(DataComponents.LORE)) {
            return false;
        }

        if (item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.BREWING_STAND
                || item == Items.FLOWER_POT) {
            return true;
        }
        if (item instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            return state.is(BlockTags.FLOWERS)
                    || blockItem.getBlock() instanceof BrewingStandBlock
                    || blockItem.getBlock() instanceof FlowerPotBlock;
        }
        return false;
    }

    public static boolean hasServerItem() {
        return ItemUtil.getAllItems().stream().anyMatch(itemStack -> {
            if (!itemStack.isEmpty()) {
                String displayName = itemStack.getDisplayName().getString();
                return displayName.contains("长按点击") || displayName.contains("点击使用") || displayName.contains("离开游戏") || displayName.contains("选择一个队伍") || displayName.contains("再来一局");
            }
            return false;
        });
    }

    public static int getSlot(ItemStack itemStack) {
        if (itemStack == null || mc.player == null) {
            return -1;
        }
        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); ++i) {
            if (mc.player.getInventory().getNonEquipmentItems().get(i) != itemStack) continue;
            return i;
        }
        return -1;
    }

    public static int getSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < mc.player.getInventory().getNonEquipmentItems().size(); ++i) {
            ItemStack itemStack = mc.player.getInventory().getNonEquipmentItems().get(i);
            if (itemStack.getItem() != item) continue;
            return i;
        }
        return -1;
    }

    public static boolean isUsableItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return true;
        }
        if (isSkyWarsJunk(itemStack)) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof BlockItem blockItem) {
            if (blockItem.getBlock() == Blocks.ENCHANTING_TABLE) {
                return false;
            }
            return blockItem.getBlock() != Blocks.COBWEB;
        }
        if (item == Items.BOOK || item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK) {
            return false;
        }
        if (item instanceof ExperienceBottleItem) {
            return false;
        }
        if (item instanceof FireworkRocketItem) {
            return false;
        }
        if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) {
            return false;
        }
        return item != Items.FLINT_AND_STEEL;
    }

    private static boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> stack.is(ItemTags.HEAD_ARMOR);
            case CHEST -> stack.is(ItemTags.CHEST_ARMOR);
            case LEGS -> stack.is(ItemTags.LEG_ARMOR);
            case FEET -> stack.is(ItemTags.FOOT_ARMOR);
            default -> false;
        };
    }

    private static int getEnchantLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (mc.level == null) return 0;
        return stack.getEnchantmentLevel(mc.level.registryAccess().holderOrThrow(enchantment));
    }

    private static double getAttributeValue(ItemStack stack, Holder<Attribute> attribute,
                                            EquipmentSlot slot, double baseValue) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double value = baseValue;

        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.slot().test(slot)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                value += entry.modifier().amount();
            }
        }
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.slot().test(slot)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                value += baseValue * entry.modifier().amount();
            }
        }
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.slot().test(slot)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                value *= 1.0 + entry.modifier().amount();
            }
        }
        return value;
    }

    public static float getDurabilityRatio(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0f;
        }
        return (float)(stack.getMaxDamage() - stack.getDamageValue()) / (float)stack.getMaxDamage();
    }

    public static int getPunchLevel(ItemStack itemStack) {
        if (mc.level == null) return 0;
        return itemStack.getEnchantmentLevel(mc.level.registryAccess().holderOrThrow(Enchantments.PUNCH));
    }

    public static int getPowerLevel(ItemStack itemStack) {
        if (mc.level == null) return 0;
        return itemStack.getEnchantmentLevel(mc.level.registryAccess().holderOrThrow(Enchantments.POWER));
    }

    public static List<ItemStack> getAllItems() {
        ArrayList<ItemStack> items = new ArrayList<>(40);
        if (mc.player == null) {
            return items;
        }
        items.addAll(mc.player.getInventory().getNonEquipmentItems());
        for (int i = 36; i < 40; i++) {
            items.add(mc.player.getInventory().getItem(i));
        }
        return items;
    }

    public static float getBestArmorScore(EquipmentSlot equipmentSlot) {
        return ItemUtil.getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && isArmorForSlot(stack, equipmentSlot))
                .map(ItemUtil::getArmorScore)
                .max(Float::compareTo)
                .orElse(0.0f);
    }

    public static float getEquippedArmorScore(EquipmentSlot equipmentSlot) {
        int slot = switch (equipmentSlot) {
            case HEAD -> 39;
            case CHEST -> 38;
            case LEGS -> 37;
            case FEET -> 36;
            default -> -1;
        };
        if (slot == -1) return 0.0f;
        return ItemUtil.getArmorScore(mc.player.getInventory().getItem(slot));
//        return 0.0f;
    }

    public static float getBestSwordDamage() {
        return ItemUtil.getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(ItemTags.SWORDS))
                .map(ItemUtil::getSwordDamage)
                .max(Float::compareTo)
                .orElse(0.0f);
    }

    public static ItemStack getBestSword() {
        return ItemUtil.getAllItems().stream()
                .filter(itemStack -> !itemStack.isEmpty() && itemStack.is(ItemTags.SWORDS))
                .max(Comparator.comparingDouble(ItemUtil::getSwordDamage))
                .orElse(null);
    }

    public static float getBowScore(ItemStack itemStack) {
        if (itemStack == null) {
            return 0.0f;
        }
        if (itemStack.isEmpty()) {
            return 0.0f;
        }
        if (itemStack.is(ItemTags.BOW_ENCHANTABLE)) {
            float score = 10.0f;
            score += getEnchantLevel(itemStack, Enchantments.PUNCH);
            score += getEnchantLevel(itemStack, Enchantments.INFINITY);
            score += getEnchantLevel(itemStack, Enchantments.FLAME);
            score += (float)getEnchantLevel(itemStack, Enchantments.POWER) / 10.0f;
            return score + getDurabilityRatio(itemStack) * 0.01f;
        }
        return 0.0f;
    }

    public static float getBowScoreAlt(ItemStack itemStack) {
        if (itemStack == null) {
            return 0.0f;
        }
        if (itemStack.isEmpty()) {
            return 0.0f;
        }
        if (itemStack.is(ItemTags.BOW_ENCHANTABLE)) {
            float score = 10.0f;
            score += (float)getEnchantLevel(itemStack, Enchantments.PUNCH) / 10.0f;
            score += getEnchantLevel(itemStack, Enchantments.INFINITY);
            score += getEnchantLevel(itemStack, Enchantments.FLAME);
            score += getEnchantLevel(itemStack, Enchantments.POWER);
            return score + getDurabilityRatio(itemStack) * 0.01f;
        }
        return 0.0f;
    }

    public static float getDigSpeed(ItemStack itemStack) {
        float speed = 0.0f;
        if (itemStack == null) {
            return 0.0f;
        }
        if (itemStack.isEmpty()) {
            return 0.0f;
        }
        if (ItemUtil.isWeaponItem(itemStack)) {
            return 0.0f;
        }
        if (ItemUtil.isLegitAxe(itemStack)) {
            return 0.0f;
        }
        if (itemStack.is(ItemTags.PICKAXES)) {
            speed += itemStack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        } else if (itemStack.getItem() instanceof AxeItem) {
            speed += itemStack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        } else if (itemStack.getItem() instanceof ShovelItem) {
            speed += itemStack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0f;
        }
        int efficiencyLevel = getEnchantLevel(itemStack, Enchantments.EFFICIENCY);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1.0f;
        }
        speed += getDurabilityRatio(itemStack) * 0.001f;
        return speed;
    }

    public static float getAxeDamage(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.is(ItemTags.AXES)) {
            return 0.0f;
        }
        float damage = (float)getAttributeValue(
                itemStack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1.0);
        int sharpnessLevel = getEnchantLevel(itemStack, Enchantments.SHARPNESS);
        if (sharpnessLevel > 0) {
            damage += sharpnessLevel * 0.5f + 0.5f;
        }
        return damage + getDurabilityRatio(itemStack) * 0.001f;
    }

    public static float getSwordDamage(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.is(ItemTags.SWORDS)) {
            return 0.0f;
        }
        float damage = (float)getAttributeValue(
                itemStack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1.0);
        int sharpnessLevel = getEnchantLevel(itemStack, Enchantments.SHARPNESS);
        if (sharpnessLevel > 0) {
            damage += sharpnessLevel * 0.5f + 0.5f;
        }
        return damage + getDurabilityRatio(itemStack) * 0.001f;
    }

    public static float getArmorScore(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 0.0f;
        }
        EquipmentSlot slot;
        if (itemStack.is(ItemTags.HEAD_ARMOR)) slot = EquipmentSlot.HEAD;
        else if (itemStack.is(ItemTags.CHEST_ARMOR)) slot = EquipmentSlot.CHEST;
        else if (itemStack.is(ItemTags.LEG_ARMOR)) slot = EquipmentSlot.LEGS;
        else if (itemStack.is(ItemTags.FOOT_ARMOR)) slot = EquipmentSlot.FEET;
        else return 0.0f;

        double armor = getAttributeValue(itemStack, Attributes.ARMOR, slot, 0.0);
        double toughness = getAttributeValue(itemStack, Attributes.ARMOR_TOUGHNESS, slot, 0.0);
        double knockbackResistance = getAttributeValue(itemStack, Attributes.KNOCKBACK_RESISTANCE, slot, 0.0);
        // Keep material as the primary ordering; enchantments only break ties
        // between pieces made from the same material.
        float score = getArmorMaterialRank(itemStack) * 10000.0f
                + (float)(armor * 100.0 + toughness * 10.0 + knockbackResistance * 100.0);
        score += getEnchantLevel(itemStack, Enchantments.PROTECTION) * 20.0f;
        score += getEnchantLevel(itemStack, Enchantments.PROJECTILE_PROTECTION) * 12.0f;
        score += getEnchantLevel(itemStack, Enchantments.BLAST_PROTECTION) * 12.0f;
        score += getEnchantLevel(itemStack, Enchantments.FIRE_PROTECTION) * 12.0f;
        score += getEnchantLevel(itemStack, Enchantments.FEATHER_FALLING) * 10.0f;
        score += getEnchantLevel(itemStack, Enchantments.THORNS) * 5.0f;
        return score + getDurabilityRatio(itemStack) * 0.01f;
    }

    private static int getArmorMaterialRank(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) return 6;
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) return 5;
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
                || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) return 4;
        if (item == Items.TURTLE_HELMET || item == Items.CHAINMAIL_HELMET
                || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS
                || item == Items.CHAINMAIL_BOOTS) return 3;
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE
                || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) return 2;
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
                || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) return 1;
        return 0;
    }

    public static float getCrossbowScore(ItemStack itemStack) {
        int score = 0;
        if (itemStack == null) {
            return 0.0f;
        }
        if (itemStack.isEmpty()) {
            return 0.0f;
        }
        if (itemStack.getItem() instanceof CrossbowItem) {
            score += getEnchantLevel(itemStack, Enchantments.QUICK_CHARGE);
            score += getEnchantLevel(itemStack, Enchantments.MULTISHOT);
            score += getEnchantLevel(itemStack, Enchantments.PIERCING);
        }
        return score;
    }

    public static boolean isWeaponItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (itemStack.is(ItemTags.AXES) && itemStack.getItem() == Items.GOLDEN_AXE && getEnchantLevel(itemStack, Enchantments.SHARPNESS) > 100) {
            return true;
        }
        if (itemStack.getItem() == Items.SLIME_BALL && getEnchantLevel(itemStack, Enchantments.KNOCKBACK) > 1) {
            return true;
        }
        if (itemStack.getItem() == Items.TOTEM_OF_UNDYING) {
            return true;
        }
        return itemStack.getItem() == Items.END_CRYSTAL;
    }

//    public static double getAttackDamage(ItemStack itemStack) {
//        double damage = 0.0;
//        Multimap<Attribute, AttributeModifier> modifiers = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
//        for (Attribute attribute : modifiers.keySet()) {
//            if (!attribute.getDescriptionId().equals("attribute.name.generic.attack_damage")) continue;
//            Iterator<AttributeModifier> iterator = modifiers.get(attribute).iterator();
//            if (!iterator.hasNext()) break;
//            damage += iterator.next().getAmount();
//            break;
//        }
//        if (itemStack.hasFoil()) {
//            damage += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, itemStack);
//            damage += (double)EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, itemStack) * 1.25;
//        }
//        return damage;
//    }

    public static boolean isSkullItem(ItemStack itemStack) {
        BlockItem blockItem;
        if (itemStack.isEmpty()) {
            return false;
        }
        Item item = itemStack.getItem();
        return item instanceof BlockItem && (blockItem = (BlockItem)item).getBlock() instanceof SkullBlock;
    }

    public static int countFishingRods() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() == Items.FISHING_ROD && ItemUtil.isUsable(itemStack)).mapToInt(ItemStack::getCount).sum();
    }

    public static int countFood() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.has(DataComponents.FOOD) && itemStack.getItem() != Items.GOLDEN_APPLE && itemStack.getItem() != Items.ENCHANTED_GOLDEN_APPLE && ItemUtil.isUsable(itemStack)).mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack getBestFoodStack() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.has(DataComponents.FOOD) && itemStack.getItem() != Items.GOLDEN_APPLE && itemStack.getItem() != Items.ENCHANTED_GOLDEN_APPLE && ItemUtil.isUsable(itemStack)).min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static boolean isLegitAxe(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (!(itemStack.getItem() instanceof AxeItem)) {
            return false;
        }
        int sharpnessLevel = getEnchantLevel(itemStack, Enchantments.SHARPNESS);
        return sharpnessLevel >= 8 && sharpnessLevel < 50;
    }

    public static boolean isOtherCheat(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        String displayName = itemStack.getDisplayName().getString();
        if (displayName.contains("一刀")) {
            return true;
        }
        if (itemStack.getComponents() != null && itemStack.getComponents().toString().contains("一刀")) {
            return true;
        }
        if (itemStack.getItem() == Items.GOLDEN_AXE) {
            return getEnchantLevel(itemStack, Enchantments.SHARPNESS) > 100;
        }
        return false;
    }

    public static boolean isEnchantedGoldenApple(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        return itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        return itemStack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isKBSlimeBall(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (itemStack.getItem() != Items.SLIME_BALL) {
            return false;
        }
        return getEnchantLevel(itemStack, Enchantments.KNOCKBACK) > 1;
    }

    public static boolean isKBStick(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (itemStack.getItem() != Items.STICK) {
            return false;
        }
        return getEnchantLevel(itemStack, Enchantments.KNOCKBACK) > 1;
    }

    public static ItemStack getBestProjectile() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && (itemStack.getItem() == Items.EGG || itemStack.getItem() == Items.SNOWBALL) && ItemUtil.isUsable(itemStack)).max(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getFishingRodStack() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof FishingRodItem && ItemUtil.isUsable(itemStack)).findAny().orElse(null);
    }

    public static int countBlocks() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(itemStack) && ItemUtil.isUsable(itemStack)).mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack getWorstProjectile() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && (itemStack.getItem() == Items.EGG || itemStack.getItem() == Items.SNOWBALL)).min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getArrowStack() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof ArrowItem && ItemUtil.isUsable(itemStack)).min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(itemStack) && ItemUtil.isUsable(itemStack)).min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getBestBlock() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(itemStack) && ItemUtil.isUsable(itemStack)).max(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static float getBestPickaxeScore() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.is(ItemTags.PICKAXES) && ItemUtil.isUsable(itemStack)).map(ItemUtil::getDigSpeed).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestPickaxe() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.is(ItemTags.PICKAXES) && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getDigSpeed)).orElse(null);
    }

    public static float getBestAxeScore() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof AxeItem && !ItemUtil.isLegitAxe(itemStack) && ItemUtil.isUsable(itemStack)).map(ItemUtil::getDigSpeed).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestAxe() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof AxeItem && !ItemUtil.isLegitAxe(itemStack) && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getDigSpeed)).orElse(null);
    }

    public static ItemStack getBestAxeForTools() {
        ItemStack regularAxe = ItemUtil.getBestAxe();
        if (regularAxe != null) {
            return regularAxe;
        }
        return ItemUtil.getAllItems().stream()
                .filter(itemStack -> !itemStack.isEmpty()
                        && itemStack.getItem() instanceof AxeItem
                        && ItemUtil.isLegitAxe(itemStack)
                        && ItemUtil.isUsable(itemStack))
                .max(Comparator.comparingDouble(ItemUtil::getDurabilityRatio)
                        .thenComparingDouble(ItemUtil::getAxeDamage))
                .orElse(null);
    }

    public static ItemStack getBestSharpAxe() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof AxeItem && ItemUtil.isLegitAxe(itemStack) && ItemUtil.isUsable(itemStack) && !ItemUtil.isOtherCheat(itemStack)).max(Comparator.comparingDouble(ItemUtil::getAxeDamage)).orElse(null);
    }

    public static float getBestShovelScore() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof ShovelItem && ItemUtil.isUsable(itemStack)).map(ItemUtil::getDigSpeed).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestShovel() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof ShovelItem && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getDigSpeed)).orElse(null);
    }

    public static float getBestCrossbowScore() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof CrossbowItem && ItemUtil.isUsable(itemStack)).map(ItemUtil::getCrossbowScore).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestCrossbow() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof CrossbowItem && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getCrossbowScore)).orElse(null);
    }

    public static float getBestBowScore() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BowItem && ItemUtil.isUsable(itemStack)).map(ItemUtil::getBowScore).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestBow() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BowItem && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getBowScore)).orElse(null);
    }

    public static float getBestBowScoreAlt() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BowItem && ItemUtil.isUsable(itemStack)).map(ItemUtil::getBowScoreAlt).max(Float::compareTo).orElse(0.0f);
    }

    public static ItemStack getBestBowAlt() {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() instanceof BowItem && ItemUtil.isUsable(itemStack)).max(Comparator.comparingDouble(ItemUtil::getBowScoreAlt)).orElse(null);
    }

    public static boolean isGoodBow(ItemStack itemStack) {
        return ItemUtil.getBowScore(itemStack) > 10.0f && ItemUtil.isUsable(itemStack);
    }

    public static boolean isGoodBowAlt(ItemStack itemStack) {
        return ItemUtil.getBowScoreAlt(itemStack) > 10.0f && ItemUtil.isUsable(itemStack);
    }

    public static boolean hasItem(Item item) {
        return ItemUtil.getAllItems().stream().anyMatch(itemStack -> !itemStack.isEmpty() && itemStack.getItem() == item);
    }

    public static int countItem(Item item) {
        return ItemUtil.getAllItems().stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    public static boolean isUsable(ItemStack itemStack) {
        if (!itemStack.isEmpty()) {
            if (itemStack.getItem() instanceof PlayerHeadItem) {
                return false;
            }
            String displayName = itemStack.getDisplayName().getString();
            if (displayName.contains("Click")) {
                return false;
            }
            if (displayName.contains("Right")) {
                return false;
            }
            if (displayName.contains("点击")) {
                return false;
            }
            if (displayName.contains("Teleport")) {
                return false;
            }
            if (displayName.contains("使用")) {
                return false;
            }
            if (displayName.contains("传送")) {
                return false;
            }
            return !displayName.contains("再来");
        }
        return true;
    }

    public static int findItemInRange(int startSlot, int endSlot, Item item) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = startSlot; i < endSlot; ++i) {
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (itemStack.isEmpty() || itemStack.getItem() != item) continue;
            return i;
        }
        return -1;
    }

    @Generated
    private ItemUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

package shit.zen.modules.impl.player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.NumberSetting;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.ItemUtil;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.event.EventTarget;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.render.WorldOverlayRenderer;

public class ChestStealer
extends Module {

    public record StealTarget(int slotIndex, ItemStack itemStack, int priority, double score) {
    }

    public static ChestStealer INSTANCE;
    private static final int PANEL_BACKGROUND_COLOR = 0xDA080A0D;
    private static final int PANEL_OUTLINE_COLOR = 0x69D2DAE6;
    private static final int EMPTY_SLOT_COLOR = 0xBE16191E;
    private static final int FILLED_SLOT_COLOR = 0xE12B3038;
    private static final int SLOT_OUTLINE_COLOR = 0x22FFFFFF;
    private static final Timer actionTimer;
    private final ModeSetting modeSetting = new ModeSetting("Mode", "Normal", "Instant").withDefault("Normal");
    private final NumberSetting clickDelaySetting = new NumberSetting(
            "Delay", 200, 0, 1000, 10, () -> !this.isInstant());
    private final NumberSetting openDelaySetting = new NumberSetting(
            "Open Delay", 2, 0, 10, 1, () -> !this.isInstant());
    private final BooleanSetting chestSetting = new BooleanSetting("Chest", true);
    private final BooleanSetting enderChestSetting = new BooleanSetting("Ender Chest", false);
    private final BooleanSetting furnaceSetting = new BooleanSetting("Furnace", true);
    private final BooleanSetting brewingStandSetting = new BooleanSetting("BrewingStand", true);
    private final BooleanSetting pickTrashSetting = new BooleanSetting("PickTrash", false);
    private final BooleanSetting onlyBestSetting = new BooleanSetting("Only Best", true);
    private final BooleanSetting randomClickSetting = new BooleanSetting("Random Click", false);
    private final BooleanSetting smartStealingSetting = new BooleanSetting("Smart Stealing", true);
    private final BooleanSetting silent = new BooleanSetting("Silent", false);
    private static final Timer stealTimer;
    private static final Timer openTimer;
    private final Random random = new Random();
    private AbstractContainerMenu pendingMenu = null;
    private boolean hasPendingClick = false;
    private int totalBlockCount = 0;
    private int pendingSlot = -1;
    private int ticksSinceMenu = 0;
    private static long clickDelayMs;
    private int accessCount;
    private Screen lastScreen;
    private int openDelayTicks = 0;
    private final List<ChestStealer.StealTarget> stealTargetQueue = new ArrayList<>();
    private int stealIndex = 0;
    private boolean queueBuilt = false;
    private BlockPos pendingContainerPos;
    private BlockPos activeContainerPos;
    private int activeContainerId = -1;

    public ChestStealer() {
        super("ChestStealer", Category.PLAYER);
        INSTANCE = this;
        NeoForge.EVENT_BUS.addListener(this::onScreenRenderPre);
        NeoForge.EVENT_BUS.addListener(this::onScreenClosing);
    }

    public static boolean isRateLimited() {
        if (INSTANCE != null && INSTANCE.isInstant()) {
            return false;
        }
        return !stealTimer.hasPassed(100L) && !openTimer.hasPassed((int)clickDelayMs);
    }

    private boolean isInstant() {
        return this.modeSetting.is("Instant");
    }

    public static boolean isSilentContainerScreen(Screen screen) {
        ChestStealer instance = INSTANCE;
        return instance != null
                && instance.isEnabled()
                && instance.silent.getValue()
                && screen instanceof ContainerScreen;
    }

    public static void captureContainerInteraction(BlockPos pos) {
        ChestStealer instance = INSTANCE;
        if (instance == null || !instance.isEnabled() || !instance.silent.getValue()
                || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof ChestBlockEntity)) {
            return;
        }
        instance.pendingContainerPos = pos.immutable();
    }

    @Override
    public void onDisable() {
        this.resetAll();
        if (mc != null && mc.mouseHandler != null && mc.mouseHandler.isMouseGrabbed()
                && mc.screen instanceof ContainerScreen) {
            KeyMapping.setAll();
            mc.mouseHandler.releaseMouse();
        }
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.resetAll();
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (!this.silent.getValue() && mc.screen instanceof ContainerScreen
                && mc.mouseHandler.isMouseGrabbed()) {
            KeyMapping.setAll();
            mc.mouseHandler.releaseMouse();
        }
        if (this.hasPendingClick && this.pendingMenu != null && this.pendingSlot >= 0) {
            ++this.ticksSinceMenu;
            if (this.ticksSinceMenu >= 1) {
                this.executePendingClick();
                this.resetState();
            }
        }
    }

    private void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!isSilentContainerScreen(event.getScreen()) || mc.player == null
                || !(event.getScreen() instanceof ContainerScreen containerScreen)) {
            return;
        }

        this.renderSilentContainer(event.getGuiGraphics(), containerScreen);
        event.setCanceled(true);
    }

    private void onScreenClosing(ScreenEvent.Closing event) {
        if (isSilentContainerScreen(event.getScreen())) {
            KeyMapping.setAll();
        }
    }

    private void renderSilentContainer(GuiGraphics graphics, ContainerScreen containerScreen) {
        ChestMenu menu = containerScreen.getMenu();
        this.bindActiveContainer(menu);

        int slotCount = menu.getRowCount() * 9;
        if (slotCount <= 0) {
            return;
        }

        int columns = 9;
        int rows = menu.getRowCount();
        int slotSize = 18;
        int slotStep = 20;
        int padding = 5;
        int titleHeight = 14;
        int contentWidth = columns * slotStep - (slotStep - slotSize);
        int contentHeight = rows * slotStep - (slotStep - slotSize);
        int panelWidth = contentWidth + padding * 2;
        int panelHeight = titleHeight + contentHeight + padding * 2;

        float centerX = graphics.guiWidth() / 2.0f;
        float centerY = graphics.guiHeight() / 2.0f;
        float[] projected = new float[2];
        Vec3 containerCenter = this.getActiveContainerCenter();
        if (containerCenter != null
                && WorldOverlayRenderer.projectToScreen(containerCenter.x, containerCenter.y, containerCenter.z, projected)
                && Float.isFinite(projected[0]) && Float.isFinite(projected[1])) {
            centerX = projected[0];
            centerY = projected[1];
        }

        int panelX = clamp(Math.round(centerX - panelWidth / 2.0f), 2,
                Math.max(2, graphics.guiWidth() - panelWidth - 2));
        int panelY = clamp(Math.round(centerY - panelHeight / 2.0f), 2,
                Math.max(2, graphics.guiHeight() - panelHeight - 2));
        int gridX = panelX + padding;
        int gridY = panelY + padding + titleHeight;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                PANEL_BACKGROUND_COLOR);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, PANEL_OUTLINE_COLOR);
        String title = mc.font.plainSubstrByWidth(containerScreen.getTitle().getString(), contentWidth);
        graphics.drawString(mc.font, title, gridX, panelY + padding, 0xFFFFFFFF, true);

        for (int index = 0; index < slotCount; ++index) {
            int x = gridX + index % columns * slotStep;
            int y = gridY + index / columns * slotStep;
            ItemStack stack = menu.getSlot(index).getItem();
            int slotColor = stack.isEmpty() ? EMPTY_SLOT_COLOR : FILLED_SLOT_COLOR;
            graphics.fill(x, y, x + slotSize, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize, slotSize, SLOT_OUTLINE_COLOR);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(mc.font, stack, x + 1, y + 1);
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null
                || mc.getConnection() == null || KillAura.target != null || Scaffold.INSTANCE.isEnabled()) {
            return;
        }
        if (!this.isInstant() && !openTimer.hasPassed((int) clickDelayMs)
                || !mc.player.isAlive() || mc.player.isDeadOrDying()
                || mc.player.isSpectator() || motionEvent.isPre()) {
            return;
        }
        Screen screen = mc.screen;
        if (screen instanceof ContainerScreen containerScreen) {
            this.bindActiveContainer(containerScreen.getMenu());
            if (!this.silent.getValue() && mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.releaseMouse();
            }
        } else {
            this.activeContainerPos = null;
            this.activeContainerId = -1;
        }
        AbstractContainerMenu containerMenu = mc.player.containerMenu;
        this.countBlocks();
        if (screen instanceof ContainerScreen containerScreen) {
            if (screen != this.lastScreen) {
                actionTimer.reset();
                this.openDelayTicks = 0;
                this.queueBuilt = false;
                this.stealTargetQueue.clear();
                this.stealIndex = 0;
            }
            if (this.isInstant()) {
                this.stealFromContainerScreen(containerScreen);
            } else if (screen == this.lastScreen) {
                ++this.openDelayTicks;
                if (this.openDelayTicks < this.openDelaySetting.getValue().intValue()) {
                    return;
                }
                this.stealFromContainerScreen(containerScreen);
            }
        } else {
            this.openDelayTicks = 0;
            this.queueBuilt = false;
            this.stealTargetQueue.clear();
            this.stealIndex = 0;
        }
        if (containerMenu instanceof FurnaceMenu furnaceMenu) {
            if (this.furnaceSetting.getValue()) {
                this.stealFromFurnace(furnaceMenu);
            }
        }
        if (containerMenu instanceof BrewingStandMenu brewingMenu) {
            if (this.brewingStandSetting.getValue()) {
                this.stealFromBrewing(brewingMenu);
            }
        }
        this.lastScreen = screen;
    }

    private void stealFromContainerScreen(ContainerScreen containerScreen) {
        String title = containerScreen.getTitle().getString();
        String chestTitle = Component.translatable("container.chest").getString();
        String doubleChestTitle = Component.translatable("container.chestDouble").getString();
        String enderChestTitle = Component.translatable("container.enderchest").getString();
        ChestMenu chestMenu = containerScreen.getMenu();
        boolean chest = this.chestSetting.getValue()
                && (title.equals(chestTitle) || title.equals(doubleChestTitle) || title.equals("Chest"));
        boolean enderChest = this.enderChestSetting.getValue() && title.equals(enderChestTitle);
        if ((chest || enderChest) && this.shouldCloseChest(chestMenu)) {
            this.stealFromChest(chestMenu);
        }
    }

    private boolean shouldCloseChest(ChestMenu chestMenu) {
        if (this.isChestDone(chestMenu) && (this.isInstant() || stealTimer.hasPassed(100L))) {
            mc.player.closeContainer();
            return false;
        }
        return true;
    }

    private void stealFromChest(ChestMenu chestMenu) {
        if (this.isInstant()) {
            this.stealInstantFromChest(chestMenu);
            return;
        }
        ++this.accessCount;
        if (this.smartStealingSetting.getValue() && this.accessCount > 1) {
            this.stealSmartMode(chestMenu);
        } else {
            this.stealRandomMode(chestMenu);
        }
    }

    private void stealInstantFromChest(ChestMenu chestMenu) {
        List<Integer> slots;
        if (this.smartStealingSetting.getValue()) {
            this.buildStealQueue(chestMenu);
            slots = this.stealTargetQueue.stream()
                    .map(StealTarget::slotIndex)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } else {
            slots = this.getStealableChestSlots(chestMenu);
        }
        if (this.randomClickSetting.getValue()) {
            java.util.Collections.shuffle(slots, this.random);
        }
        this.executeInstantClicks(chestMenu, slots);
        if (this.isChestComplete(chestMenu)) {
            mc.player.closeContainer();
        }
    }

    private void stealSmartMode(ChestMenu chestMenu) {
        if (!this.queueBuilt) {
            this.buildStealQueue(chestMenu);
            this.queueBuilt = true;
            this.stealIndex = 0;
        }
        if (this.stealIndex < this.stealTargetQueue.size()) {
            ChestStealer.StealTarget target = this.stealTargetQueue.get(this.stealIndex);
            if (!chestMenu.getSlot(target.slotIndex).getItem().isEmpty()) {
                this.schedulePendingClick(chestMenu, target.slotIndex);
                ++this.stealIndex;
            } else {
                ++this.stealIndex;
            }
        } else if (this.isChestComplete(chestMenu) && stealTimer.hasPassed(100L)) {
            mc.player.closeContainer();
        } else {
            // A server-side rollback leaves the item in the chest after its
            // original queue entry has been consumed. Rebuild the queue so it
            // is retried instead of leaving the stealer idle.
            this.queueBuilt = false;
            this.stealTargetQueue.clear();
            this.stealIndex = 0;
        }
    }

    private void buildStealQueue(ChestMenu chestMenu) {
        ArrayList<ChestStealer.StealTarget> candidates = new ArrayList<>();
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            int priority = this.getItemPriority(itemStack);
            double score = this.getItemScore(itemStack);
            candidates.add(new ChestStealer.StealTarget(slot, itemStack, priority, score));
        }
        Map<String, List<ChestStealer.StealTarget>> categoryMap = this.categorizeItems(candidates);
        this.stealTargetQueue.clear();
        List<String> categories = Arrays.asList("god", "helmet", "chestplate", "leggings", "boots", "sword", "bow", "crossbow", "golden_apple", "pickaxe", "axe", "shovel", "special", "utility", "other");
        for (String category : categories) {
            if (!categoryMap.containsKey(category)) continue;
            List<ChestStealer.StealTarget> categoryItems = categoryMap.get(category);
            boolean isBestOnlyCategory = category.equals("god") || category.equals("helmet") || category.equals("chestplate") || category.equals("leggings") || category.equals("boots") || category.equals("sword") || category.equals("bow") || category.equals("crossbow") || category.equals("pickaxe") || category.equals("axe") || category.equals("shovel");
            if (this.onlyBestSetting.getValue() && isBestOnlyCategory) {
                ChestStealer.StealTarget best = categoryItems.stream().max(Comparator.comparingDouble(t -> t.score)).orElse(null);
                if (best == null) continue;
                this.stealTargetQueue.add(best);
                continue;
            }
            categoryItems.sort((a, b) -> Double.compare(b.score, a.score));
            this.stealTargetQueue.addAll(categoryItems);
        }
    }

    private Map<String, List<ChestStealer.StealTarget>> categorizeItems(List<ChestStealer.StealTarget> targets) {
        HashMap<String, List<ChestStealer.StealTarget>> categoryMap = new HashMap<>();
        for (ChestStealer.StealTarget target : targets) {
            String category = this.getItemCategory(target.itemStack);
            categoryMap.computeIfAbsent(category, key -> new ArrayList<>()).add(target);
        }
        return categoryMap;
    }

    private String getItemCategory(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return "god";
        }
        Equippable equippable = item.getDefaultInstance().get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            return switch (slot) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> "other";
            };
        }
        if (item.getDefaultInstance().is(ItemTags.SWORDS)) {
            return "sword";
        }
        if (item instanceof BowItem) {
            return "bow";
        }
        if (item instanceof CrossbowItem) {
            return "crossbow";
        }
        if (item.getDefaultInstance().is(ItemTags.PICKAXES)) {
            return "pickaxe";
        }
        if (item instanceof AxeItem) {
            return "axe";
        }
        if (item instanceof ShovelItem) {
            return "shovel";
        }
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return "golden_apple";
        }
        if (item == Items.COMPASS || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return "special";
        }
        if (item == Items.COBWEB) {
            return "utility";
        }
        if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG || item == Items.ARROW || item instanceof FishingRodItem || item instanceof BlockItem) {
            return "utility";
        }
        return "other";
    }

    private int getItemPriority(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return 150;
        }
        Equippable equippable = item.getDefaultInstance().get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            return switch (slot) {
                case HEAD -> 100;
                case CHEST -> 99;
                case LEGS -> 98;
                case FEET -> 97;
                default -> 50;
            };
        }
        if (item.getDefaultInstance().is(ItemTags.SWORDS)) {
            return 95;
        }
        if (item instanceof BowItem) {
            return 93;
        }
        if (item instanceof CrossbowItem) {
            return 92;
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 91;
        }
        if (item == Items.GOLDEN_APPLE) {
            return 90;
        }
        if (item.getDefaultInstance().is(ItemTags.PICKAXES)) {
            return 89;
        }
        if (item instanceof AxeItem) {
            return 88;
        }
        if (item instanceof ShovelItem) {
            return 87;
        }
        if (item == Items.COMPASS) {
            return 85;
        }
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return 83;
        }
        if (item == Items.ENDER_PEARL) {
            return 80;
        }
        if (item == Items.ARROW) {
            return 75;
        }
        if (item == Items.COBWEB) {
            return 72;
        }
        if (item == Items.SNOWBALL || item == Items.EGG) {
            return 70;
        }
        if (item instanceof FishingRodItem) {
            return 65;
        }
        if (item instanceof BlockItem) {
            return 60;
        }
        return 50;
    }

    private double getItemScore(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return 10000.0;
        }
        if (itemStack.is(ItemTags.HEAD_ARMOR) ||
                itemStack.is(ItemTags.CHEST_ARMOR) ||
                itemStack.is(ItemTags.LEG_ARMOR) ||
                itemStack.is(ItemTags.FOOT_ARMOR)) {
            return ItemUtil.getArmorScore(itemStack);
        }
        if (itemStack.is(ItemTags.SWORDS)) {
            return ItemUtil.getSwordDamage(itemStack);
        }
        if (item instanceof AxeItem && ItemUtil.isLegitAxe(itemStack)) {
            return ItemUtil.getAxeDamage(itemStack);
        }
        if (itemStack.has(DataComponents.TOOL)) {
            return ItemUtil.getDigSpeed(itemStack);
        }
        if (item instanceof BowItem) {
            if (ItemUtil.isGoodBow(itemStack)) {
                return ItemUtil.getBowScore(itemStack);
            }
            if (ItemUtil.isGoodBowAlt(itemStack)) {
                return ItemUtil.getBowScoreAlt(itemStack);
            }
            return 1.0;
        }
        if (item instanceof CrossbowItem) {
            return ItemUtil.getCrossbowScore(itemStack);
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 50.0 + (double)itemStack.getCount();
        }
        if (item == Items.GOLDEN_APPLE) {
            return 30.0 + (double)itemStack.getCount();
        }
        if (item == Items.ENDER_PEARL) {
            return 10.0 + (double)itemStack.getCount();
        }
        if (item == Items.ARROW) {
            return 5.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item == Items.COBWEB) {
            return 4.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item == Items.SNOWBALL || item == Items.EGG) {
            return 3.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item instanceof FishingRodItem) {
            return ItemUtil.getDigSpeed(itemStack);
        }
        if (item instanceof BlockItem) {
            return 2.0 + (double)itemStack.getCount() * 0.05;
        }
        return 1.0;
    }

    private void stealRandomMode(ChestMenu chestMenu) {
        List<Integer> stealableSlots = this.getStealableChestSlots(chestMenu);
        if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
            int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
            this.schedulePendingClick(chestMenu, randomSlot);
        } else {
            for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
                ItemStack itemStack = chestMenu.getSlot(slot).getItem();
                if (itemStack.isEmpty() || this.accessCount <= 1 || !this.tryStealSlot(chestMenu, slot)) continue;
                return;
            }
        }
    }

    private void stealFromFurnace(FurnaceMenu furnaceMenu) {
        if (this.isInstant()) {
            try {
                Container container = this.getFurnaceContainer(furnaceMenu);
                if (container != null) {
                    this.executeInstantClicks(furnaceMenu, this.getStealableContainerSlots(container));
                    if (this.isFurnaceDone(furnaceMenu)) {
                        mc.player.closeContainer();
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return;
        }
        ++this.accessCount;
        try {
            Container container = this.getFurnaceContainer(furnaceMenu);
            if (container == null) {
                return;
            }
            if (this.isFurnaceDone(furnaceMenu) && stealTimer.hasPassed(100L)) {
                mc.player.closeContainer();
                return;
            }
            List<Integer> stealableSlots = this.getStealableContainerSlots(container);
            if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
                int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
                this.schedulePendingClick(furnaceMenu, randomSlot);
            } else {
                for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                    ItemStack itemStack = container.getItem(slot);
                    if (itemStack.isEmpty() || this.accessCount <= 1 || !this.shouldStealItem(itemStack)) continue;
                    this.schedulePendingClick(furnaceMenu, slot);
                    return;
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void stealFromBrewing(BrewingStandMenu brewingStandMenu) {
        if (this.isInstant()) {
            Container container = ReflectionUtil.getBrewingStand(brewingStandMenu);
            if (container != null) {
                this.executeInstantClicks(brewingStandMenu, this.getStealableContainerSlots(container));
                if (this.isBrewingDone(brewingStandMenu)) {
                    mc.player.closeContainer();
                }
            }
            return;
        }
        ++this.accessCount;
        Container container = ReflectionUtil.getBrewingStand(brewingStandMenu);
        if (container == null) {
            return;
        }
        if (this.isBrewingDone(brewingStandMenu) && stealTimer.hasPassed(100L)) {
            mc.player.closeContainer();
            return;
        }
        List<Integer> stealableSlots = this.getStealableContainerSlots(container);
        if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
            int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
            this.schedulePendingClick(brewingStandMenu, randomSlot);
        } else {
            for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                ItemStack itemStack = container.getItem(slot);
                if (itemStack.isEmpty() || this.accessCount <= 1 || !this.shouldStealItem(itemStack)) continue;
                this.schedulePendingClick(brewingStandMenu, slot);
                return;
            }
        }
    }

    private List<Integer> getStealableChestSlots(ChestMenu chestMenu) {
        ArrayList<Integer> stealableSlots = new ArrayList<>();
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            stealableSlots.add(slot);
        }
        return stealableSlots;
    }

    private List<Integer> getStealableContainerSlots(Container container) {
        ArrayList<Integer> stealableSlots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack itemStack = container.getItem(slot);
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            stealableSlots.add(slot);
        }
        return stealableSlots;
    }

    private Container getFurnaceContainer(AbstractFurnaceMenu furnaceMenu) throws Exception {
        Field[] fields;
        for (Field field : fields = AbstractFurnaceMenu.class.getDeclaredFields()) {
            if (!Container.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            return (Container)field.get(furnaceMenu);
        }
        return null;
    }

    private void schedulePendingClick(AbstractContainerMenu menu, int slot) {
        if (!this.hasPendingClick) {
            this.pendingMenu = menu;
            this.pendingSlot = slot;
            this.hasPendingClick = true;
            this.ticksSinceMenu = 0;
        }
    }

    private void executePendingClick() {
        if (this.pendingMenu != null && this.pendingSlot >= 0) {
            clickDelayMs = this.clickDelaySetting.getValue().longValue();
            mc.gameMode.handleInventoryMouseClick(this.pendingMenu.containerId, this.pendingSlot, 0, ClickType.QUICK_MOVE, mc.player);
            openTimer.reset();
            stealTimer.reset();
            actionTimer.reset();
        }
    }

    private void executeInstantClicks(AbstractContainerMenu menu, List<Integer> slots) {
        this.resetState();
        int clicks = 0;
        for (int slot : slots) {
            if (clicks >= 128) {
                break;
            }
            if (menu.getSlot(slot).getItem().isEmpty()) {
                continue;
            }
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
            clicks++;
        }
        openTimer.reset();
        stealTimer.reset();
        actionTimer.reset();
        this.countBlocks();
    }

    private boolean tryStealSlot(ChestMenu chestMenu, int slot) {
        ItemStack itemStack = chestMenu.getSlot(slot).getItem();
        if ((ChestStealer.isWorthStealing(itemStack) || this.pickTrashSetting.getValue()) && this.shouldStealItem(itemStack)) {
            this.schedulePendingClick(chestMenu, slot);
            return true;
        }
        return false;
    }

    private void resetAll() {
        this.resetState();
        this.openDelayTicks = 0;
        this.pendingContainerPos = null;
        this.activeContainerPos = null;
        this.activeContainerId = -1;
    }

    private void bindActiveContainer(ChestMenu menu) {
        if (menu.containerId == this.activeContainerId) {
            return;
        }
        this.activeContainerId = menu.containerId;
        this.activeContainerPos = this.pendingContainerPos;
        this.pendingContainerPos = null;

        if (this.activeContainerPos == null && mc.hitResult instanceof BlockHitResult hitResult
                && mc.level.getBlockEntity(hitResult.getBlockPos()) instanceof ChestBlockEntity) {
            this.activeContainerPos = hitResult.getBlockPos().immutable();
        }
    }

    private Vec3 getActiveContainerCenter() {
        if (this.activeContainerPos == null || mc.level == null
                || !(mc.level.getBlockEntity(this.activeContainerPos) instanceof ChestBlockEntity)) {
            return null;
        }

        Vec3 center = Vec3.atCenterOf(this.activeContainerPos).add(0.0, -0.0625, 0.0);
        BlockState state = mc.level.getBlockState(this.activeContainerPos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return center;
        }

        BlockPos otherPos = this.activeContainerPos.relative(ChestBlock.getConnectedDirection(state));
        if (mc.level.getBlockEntity(otherPos) instanceof ChestBlockEntity) {
            center = center.add(
                    (otherPos.getX() - this.activeContainerPos.getX()) * 0.5,
                    0.0,
                    (otherPos.getZ() - this.activeContainerPos.getZ()) * 0.5
            );
        }
        return center;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void resetState() {
        this.hasPendingClick = false;
        this.pendingSlot = -1;
        this.pendingMenu = null;
        this.ticksSinceMenu = 0;
    }

    private void countBlocks() {
        this.totalBlockCount = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); ++slot) {
            ItemStack itemStack = mc.player.getInventory().getItem(slot);
            if (itemStack.isEmpty()
                    || itemStack.getItem() == Items.COBWEB
                    || !isSolidBlockItem(itemStack)
                    || !ItemUtil.isUsableItem(itemStack)) continue;
            this.totalBlockCount += itemStack.getCount();
        }
    }

    private boolean shouldStealItem(ItemStack itemStack) {
        int count;
        Item item = itemStack.getItem();
        // These items are never useful in SkyWars, even when trash pickup is enabled.
        if (ItemUtil.isSkyWarsJunk(itemStack)) {
            return false;
        }
        if (item instanceof BlockItem && item != Items.COBWEB
                && !BlockUtil.isSafeBridgeBlock(itemStack)
                && !this.pickTrashSetting.getValue()) {
            return false;
        }
        if (item instanceof FishingRodItem && (count = ItemUtil.countItem(Items.FISHING_ROD)) > 0) {
            return false;
        }
        if (item instanceof BlockItem && item != Items.COBWEB) {
            count = InventoryManager.getMaxBlockSize();
            if (this.totalBlockCount + itemStack.getCount() > count) {
                return false;
            }
        }
        if (this.onlyBestSetting.getValue()) {
            if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
                return true;
            }
            if (itemStack.is(ItemTags.SWORDS)) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (itemStack.has(DataComponents.TOOL)) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (itemStack.is(ItemTags.HEAD_ARMOR) ||
                    itemStack.is(ItemTags.CHEST_ARMOR) ||
                    itemStack.is(ItemTags.LEG_ARMOR) ||
                    itemStack.is(ItemTags.FOOT_ARMOR)) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof BowItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof CrossbowItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (!(itemStack.is(ItemTags.SWORDS) ||
                    itemStack.is(ItemTags.PICKAXES) ||
                    itemStack.is(ItemTags.AXES) ||
                    itemStack.is(ItemTags.SHOVELS) ||
                    itemStack.is(ItemTags.HOES) ||
                    itemStack.is(ItemTags.HEAD_ARMOR) ||
                    itemStack.is(ItemTags.CHEST_ARMOR) ||
                    itemStack.is(ItemTags.LEG_ARMOR) ||
                    itemStack.is(ItemTags.FOOT_ARMOR) ||
                    item instanceof BowItem ||
                    item instanceof CrossbowItem)) {
                return ChestStealer.isWorthStealing(itemStack) || this.pickTrashSetting.getValue() != false;
            }
        }
        return true;
    }

    private boolean isBetterThanCurrent(ItemStack itemStack) {
        if (itemStack.is(ItemTags.SWORDS)) {
            float candidateDamage = ItemUtil.getSwordDamage(itemStack);
            float bestDamage = ItemUtil.getBestSwordDamage();
            return candidateDamage > bestDamage;
        }
        if (itemStack.is(ItemTags.PICKAXES) ||
                itemStack.is(ItemTags.AXES) ||
                itemStack.is(ItemTags.SHOVELS) ||
                itemStack.is(ItemTags.HOES)) {
            if (itemStack.is(ItemTags.PICKAXES)) {
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestPickaxeScore();
                return candidateSpeed > bestSpeed;
            }
            if (itemStack.getItem() instanceof AxeItem) {
                if (ItemUtil.isLegitAxe(itemStack)) {
                    float candidateDamage = ItemUtil.getAxeDamage(itemStack);
                    ItemStack bestAxeStack = ItemUtil.getBestSharpAxe();
                    float bestDamage = bestAxeStack != null ? ItemUtil.getAxeDamage(bestAxeStack) : 0.0f;
                    return candidateDamage > bestDamage;
                }
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestAxeScore();
                return candidateSpeed > bestSpeed;
            }
            if (itemStack.getItem() instanceof ShovelItem) {
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestShovelScore();
                return candidateSpeed > bestSpeed;
            }
        } else {
            Item item = itemStack.getItem();
            Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
            if (equippable != null) {
                float candidateScore = ItemUtil.getArmorScore(itemStack);
                float equippedScore = ItemUtil.getEquippedArmorScore(equippable.slot());
                return candidateScore > equippedScore + 0.1f;
            }
            if (itemStack.getItem() instanceof BowItem) {
                if (ItemUtil.isGoodBow(itemStack)) {
                    float candidateScore = ItemUtil.getBowScore(itemStack);
                    float bestScore = ItemUtil.getBestBowScore();
                    return candidateScore > bestScore;
                }
                if (ItemUtil.isGoodBowAlt(itemStack)) {
                    float candidateScore = ItemUtil.getBowScoreAlt(itemStack);
                    float bestScore = ItemUtil.getBestBowScoreAlt();
                    return candidateScore > bestScore;
                }
            } else if (itemStack.getItem() instanceof CrossbowItem) {
                float candidateScore = ItemUtil.getCrossbowScore(itemStack);
                float bestScore = ItemUtil.getBestCrossbowScore();
                return candidateScore > bestScore;
            }
        }
        return true;
    }

    private boolean isChestDone(ChestMenu chestMenu) {
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    private boolean isChestComplete(ChestMenu chestMenu) {
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    private boolean isFurnaceDone(FurnaceMenu furnaceMenu) {
        try {
            Container container = this.getFurnaceContainer(furnaceMenu);
            if (container == null) {
                return false;
            }
            for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                ItemStack itemStack = container.getItem(slot);
                if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
                return false;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean isBrewingDone(BrewingStandMenu brewingStandMenu) {
        Container container = ReflectionUtil.getBrewingStand(brewingStandMenu);
        if (container == null) {
            return true;
        }
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack itemStack = container.getItem(slot);
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    public static boolean isWorthStealing(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (ItemUtil.isSkyWarsJunk(itemStack)) {
            return false;
        }
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack) || ItemUtil.isLegitAxe(itemStack)) {
            return true;
        }
        Item item = itemStack.getItem();
        if (itemStack.get(DataComponents.EQUIPPABLE) instanceof Equippable equippable) {
            float candidateScore = ItemUtil.getArmorScore(itemStack);
            float bestScore = ItemUtil.getBestArmorScore(equippable.slot());
            return !(candidateScore <= bestScore);
        }
        if (itemStack.is(ItemTags.SWORDS)) {
            float candidateDamage = ItemUtil.getSwordDamage(itemStack);
            float bestDamage = ItemUtil.getBestSwordDamage();
            return !(candidateDamage <= bestDamage);
        }
        if (itemStack.is(ItemTags.PICKAXES)) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestPickaxeScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof AxeItem) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestAxeScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof ShovelItem) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestShovelScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof CrossbowItem) {
            float candidateScore = ItemUtil.getCrossbowScore(itemStack);
            float bestScore = ItemUtil.getBestCrossbowScore();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() instanceof BowItem && ItemUtil.isGoodBow(itemStack)) {
            float candidateScore = ItemUtil.getBowScore(itemStack);
            float bestScore = ItemUtil.getBestBowScore();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() instanceof BowItem && ItemUtil.isGoodBowAlt(itemStack)) {
            float candidateScore = ItemUtil.getBowScoreAlt(itemStack);
            float bestScore = ItemUtil.getBestBowScoreAlt();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() == Items.GOLDEN_APPLE || itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            return true;
        }
        if (itemStack.getItem() == Items.COBWEB) {
            return true;
        }
        if (itemStack.getItem() == Items.COMPASS) {
            return !ItemUtil.hasItem(itemStack.getItem());
        }
        if (itemStack.getItem() == Items.WATER_BUCKET && ItemUtil.countItem(Items.WATER_BUCKET) >= InventoryManager.getMaxWaterBuckets()) {
            return false;
        }
        if (itemStack.getItem() == Items.LAVA_BUCKET && ItemUtil.countItem(Items.LAVA_BUCKET) >= InventoryManager.getMaxLavaBuckets()) {
            return false;
        }
        if (itemStack.getItem() instanceof BlockItem) {
            return BlockUtil.isSafeBridgeBlock(itemStack)
                    && ItemUtil.isUsableItem(itemStack)
                    && ItemUtil.countBlocks() + itemStack.getCount() <= InventoryManager.getMaxBlockSize();
        }
        if (itemStack.getItem() == Items.ARROW && ItemUtil.countItem(Items.ARROW) + itemStack.getCount() >= InventoryManager.getMaxArrows()) {
            return false;
        }
        if (itemStack.getItem() instanceof FishingRodItem && ItemUtil.countItem(Items.FISHING_ROD) >= 1) {
            return false;
        }
        if ((itemStack.getItem() == Items.SNOWBALL || itemStack.getItem() == Items.EGG) && ItemUtil.countItem(Items.SNOWBALL) + ItemUtil.countItem(Items.EGG) + itemStack.getCount() >= InventoryManager.getMaxEggsSnowballsSize()) {
            return false;
        }
        return ItemUtil.isUsableItem(itemStack);
    }

    public static boolean isSolidBlockItem(ItemStack stack) {
        return BlockUtil.isSafeBridgeBlock(stack);
    }

    static {
        actionTimer = new Timer();
        stealTimer = new Timer();
        openTimer = new Timer();
    }
}

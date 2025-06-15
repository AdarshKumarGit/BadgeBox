package org.chubby.github.badgebox;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.client.BadgeClientNetworking;
import java.util.*;

public class BadgeEditorScreenHandler extends GenericContainerScreenHandler {
    private final PlayerEntity player;
    private final PlayerBadgeData playerData;
    private String currentCategory = "all";
    private int currentPage = 0;
    private static final int BADGES_PER_PAGE = 28; // 4 rows of 7 badges

    // Slot ranges for different sections
    private static final int CATEGORY_START = 0;
    private static final int CATEGORY_END = 6;
    private static final int BADGE_AREA_START = 9;
    private static final int BADGE_AREA_END = 36;
    private static final int DISPLAY_CASE_START = 46;
    private static final int DISPLAY_CASE_END = 53;
    private static final int NAV_PREV = 45;
    private static final int NAV_NEXT = 53;

    public BadgeEditorScreenHandler(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(54), 6);
        this.player = player;
        this.playerData = BadgeDataManager.getPlayerData(player.getUuid());
        setupGui();
    }

    // Custom slot for display case - accepts badge drops
    private class DisplayCaseSlot extends Slot {
        public DisplayCaseSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            // Check if this is a badge that the player owns
            return isBadgeOwnedByPlayer(stack);
        }

        @Override
        public void setStack(ItemStack stack) {
            super.setStack(stack);
            // Update the display case data when a badge is placed
            int displayIndex = this.getIndex() - DISPLAY_CASE_START;
            if (displayIndex >= 0 && displayIndex < 8) {
                Identifier badgeId = getBadgeIdFromStack(stack);
                playerData.setDisplayBadge(displayIndex, badgeId);

                // Send update to server
                if (player.getWorld().isClient) {
                    BadgeClientNetworking.updateBadgeDisplay(displayIndex, badgeId);
                } else {
                    // Server-side: save and broadcast
                    BadgeDataManager.savePlayerData();
                    if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                        BadgeNetworking.broadcastBadgeDataUpdate(serverPlayer);
                    }
                }
            }
        }

        @Override
        public ItemStack takeStack(int amount) {
            ItemStack result = super.takeStack(amount);
            if (!result.isEmpty()) {
                // Update the display case data when a badge is removed
                int displayIndex = this.getIndex() - DISPLAY_CASE_START;
                if (displayIndex >= 0 && displayIndex < 8) {
                    playerData.setDisplayBadge(displayIndex, null);

                    // Send update to server
                    if (player.getWorld().isClient) {
                        BadgeClientNetworking.updateBadgeDisplay(displayIndex, null);
                    } else {
                        // Server-side: save and broadcast
                        BadgeDataManager.savePlayerData();
                        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                            BadgeNetworking.broadcastBadgeDataUpdate(serverPlayer);
                        }
                    }
                }
            }
            return result;
        }
    }

    private boolean isBadgeOwnedByPlayer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier badgeId = getBadgeIdFromStack(stack);
        return badgeId != null && playerData.hasBadge(badgeId);
    }

    private Identifier getBadgeIdFromStack(ItemStack stack) {
        // Extract badge ID from the item stack's custom data
        if (stack.get(DataComponentTypes.CUSTOM_NAME) != null) {
            String name = stack.getName().getString();
            // Remove formatting codes
            String cleanName = name.replaceAll("§[0-9a-fk-or]", "");

            for (Badge badge : BadgeRegistry.getAllBadges()) {
                if (badge.getName().equals(cleanName)) {
                    return badge.getId();
                }
            }
        }
        return null;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Handle category selection
        if (slotIndex >= CATEGORY_START && slotIndex <= CATEGORY_END) {
            handleCategorySelection(slotIndex);
            return;
        }

        // Handle navigation
        if (slotIndex == NAV_PREV) {
            if (currentPage > 0) {
                currentPage--;
                setupGui();
            }
            return;
        }

        if (slotIndex == NAV_NEXT) {
            List<Badge> badges = getBadgesForCurrentCategory();
            if ((currentPage + 1) * BADGES_PER_PAGE < badges.size()) {
                currentPage++;
                setupGui();
            }
            return;
        }

        // Handle badge area clicks - create virtual badge items for dragging
        if (slotIndex >= BADGE_AREA_START && slotIndex <= BADGE_AREA_END) {
            ItemStack clickedStack = this.getInventory().getStack(slotIndex);
            if (!clickedStack.isEmpty() && isBadgeOwnedByPlayer(clickedStack)) {
                // Create a copy for the cursor but don't actually remove from slot
                this.setCursorStack(clickedStack.copy());
                return;
            }
        }

        // Allow normal interaction for display case slots only
        if (slotIndex >= DISPLAY_CASE_START && slotIndex <= DISPLAY_CASE_END) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        // Block all other slot interactions
        if (slotIndex >= 0 && slotIndex < 54) {
            return;
        }

        // Allow normal inventory interactions for player inventory
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void handleCategorySelection(int slotIndex) {
        List<String> categories = new ArrayList<>(BadgeRegistry.getCategories());
        categories.add(0, "all");

        if (slotIndex < categories.size()) {
            currentCategory = categories.get(slotIndex);
            currentPage = 0;
            setupGui();
        }
    }

    private void setupGui() {
        Inventory inventory = getInventory();

        // Clear inventory
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        // Setup category selector (top row)
        setupCategorySelector();

        // Setup badge display area
        setupBadgeDisplay();

        // Setup navigation
        setupNavigation();

        // Setup display case preview
        setupDisplayCasePreview();
    }

    private void setupCategorySelector() {
        Inventory inventory = getInventory();
        Set<String> categories = BadgeRegistry.getCategories();
        List<String> categoryList = new ArrayList<>(categories);
        categoryList.add(0, "all");

        for (int i = 0; i < Math.min(7, categoryList.size()); i++) {
            String category = categoryList.get(i);
            ItemStack categoryItem = createCategoryItem(category);

            // Highlight current category
            if (category.equals(currentCategory)) {
                categoryItem.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            inventory.setStack(i, categoryItem);
        }
    }

    private ItemStack createCategoryItem(String category) {
        ItemStack item;
        String displayName;

        switch (category) {
            case "all" -> {
                item = new ItemStack(Items.COMPASS);
                displayName = "§eAll Badges";
            }
            case "gym" -> {
                item = new ItemStack(Items.DIAMOND);
                displayName = "§bGym Badges";
            }
            case "elite_four" -> {
                item = new ItemStack(Items.EMERALD);
                displayName = "§dElite Four";
            }
            case "champion" -> {
                item = new ItemStack(Items.GOLD_INGOT);
                displayName = "§6Champion";
            }
            case "special" -> {
                item = new ItemStack(Items.NETHER_STAR);
                displayName = "§5Special";
            }
            case "event" -> {
                item = new ItemStack(Items.FIREWORK_ROCKET);
                displayName = "§aEvent";
            }
            default -> {
                item = new ItemStack(Items.PAPER);
                displayName = "§7" + category.substring(0, 1).toUpperCase() + category.substring(1).replace("_", " ");
            }
        }

        item.set(DataComponentTypes.ITEM_NAME, Text.literal(displayName));
        item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7Click to view " + displayName.toLowerCase().replaceAll("§[0-9a-fk-or]", ""))
        )));

        return item;
    }

    private void setupBadgeDisplay() {
        Inventory inventory = getInventory();
        List<Badge> badges = getBadgesForCurrentCategory();

        int startIndex = currentPage * BADGES_PER_PAGE;
        int endIndex = Math.min(startIndex + BADGES_PER_PAGE, badges.size());

        for (int i = 0; i < BADGES_PER_PAGE; i++) {
            int slot = BADGE_AREA_START + i;

            if (startIndex + i < endIndex) {
                Badge badge = badges.get(startIndex + i);
                ItemStack badgeItem = createBadgeDisplayItem(badge);
                inventory.setStack(slot, badgeItem);
            } else {
                inventory.setStack(slot, createEmptySlotItem());
            }
        }
    }

    private List<Badge> getBadgesForCurrentCategory() {
        if ("all".equals(currentCategory)) {
            return new ArrayList<>(BadgeRegistry.getAllBadges());
        } else {
            return BadgeRegistry.getBadgesByCategory(currentCategory);
        }
    }

    private ItemStack createBadgeDisplayItem(Badge badge) {
        boolean owned = playerData.hasBadge(badge.getId());
        ItemStack item;

        if (owned) {
            item = badge.getDisplayItem().copy();
        } else {
            // Create silhouette item - use the same item but make it darker/grayed out
            item = badge.getDisplayItem().copy();
            // You might want to create a getSilhouetteItem() method in Badge class
            // For now, we'll just use a different naming convention
        }

        // Set custom name
        String nameColor = owned ? "§e" : "§8";
        item.set(DataComponentTypes.ITEM_NAME, Text.literal(nameColor + badge.getName()));

        // Add custom lore
        List<Text> loreList = new ArrayList<>();
        badge.getLore().forEach(line -> loreList.add(Text.literal("§7" + line)));

        loreList.add(Text.literal(""));
        if (owned) {
            loreList.add(Text.literal("§aOwned"));
            if (isInDisplayCase(badge.getId())) {
                loreList.add(Text.literal("§eCurrently in Display Case"));
            } else {
                loreList.add(Text.literal("§7Click to add to Display Case"));
            }
        } else {
            loreList.add(Text.literal("§cNot Owned"));
            loreList.add(Text.literal("§7Cannot be displayed"));
        }

        item.set(DataComponentTypes.LORE, new LoreComponent(loreList));
        return item;
    }

    private boolean isInDisplayCase(Identifier badgeId) {
        return playerData.getDisplayBadges().contains(badgeId);
    }

    private ItemStack createEmptySlotItem() {
        ItemStack item = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
        item.set(DataComponentTypes.ITEM_NAME, Text.literal("§7Empty Slot"));
        return item;
    }

    private void setupNavigation() {
        Inventory inventory = getInventory();
        List<Badge> badges = getBadgesForCurrentCategory();
        int totalPages = Math.max(1, (badges.size() + BADGES_PER_PAGE - 1) / BADGES_PER_PAGE);

        // Previous page
        if (currentPage > 0) {
            ItemStack prevItem = new ItemStack(Items.ARROW);
            prevItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§aPrevious Page"));
            prevItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7Page " + (currentPage + 1) + " of " + totalPages)
            )));
            inventory.setStack(NAV_PREV, prevItem);
        }

        // Next page
        if ((currentPage + 1) * BADGES_PER_PAGE < badges.size()) {
            ItemStack nextItem = new ItemStack(Items.ARROW);
            nextItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§aNext Page"));
            nextItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7Page " + (currentPage + 2) + " of " + totalPages)
            )));
            inventory.setStack(NAV_NEXT, nextItem);
        }
    }

    private void setupDisplayCasePreview() {
        Inventory inventory = getInventory();
        List<Identifier> displayBadges = playerData.getDisplayBadges();

        // Clear display case slots first
        for (int i = 0; i < 8; i++) {
            inventory.setStack(DISPLAY_CASE_START + i, ItemStack.EMPTY);
        }

        // Setup display case slots
        for (int i = 0; i < 8; i++) {
            int slot = DISPLAY_CASE_START + i;

            // Replace the slot with our custom DisplayCaseSlot
            this.slots.set(slot, new DisplayCaseSlot(inventory, slot, 8 + (slot % 9) * 18, 18 + (slot / 9) * 18));

            Identifier badgeId = i < displayBadges.size() ? displayBadges.get(i) : null;

            if (badgeId != null) {
                Badge badge = BadgeRegistry.getBadge(badgeId);
                if (badge != null && playerData.hasBadge(badgeId)) {
                    ItemStack item = badge.getDisplayItem().copy();
                    item.set(DataComponentTypes.ITEM_NAME, Text.literal("§e" + badge.getName()));
                    item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§7In Display Case"),
                            Text.literal("§7Click to remove")
                    )));
                    inventory.setStack(slot, item);
                }
            } else {
                ItemStack emptyItem = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                emptyItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§7Empty Display Slot"));
                emptyItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("§7Drag a badge here to display it")
                )));
                inventory.setStack(slot, emptyItem);
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // Handle shift-clicking from badge area to display case
        if (slot >= BADGE_AREA_START && slot <= BADGE_AREA_END) {
            ItemStack clickedStack = this.getSlot(slot).getStack();
            Identifier badgeId = getBadgeIdFromStack(clickedStack);

            if (badgeId != null && playerData.hasBadge(badgeId)) {
                // Find first empty display slot
                for (int i = 0; i < 8; i++) {
                    if (playerData.getDisplayBadge(i) == null) {
                        playerData.setDisplayBadge(i, badgeId);

                        // Send update to server
                        if (player.getWorld().isClient) {
                            BadgeClientNetworking.updateBadgeDisplay(i, badgeId);
                        } else {
                            BadgeDataManager.savePlayerData();
                            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                                BadgeNetworking.broadcastBadgeDataUpdate(serverPlayer);
                            }
                        }

                        // Refresh the GUI to show changes
                        this.setupDisplayCasePreview();
                        break;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        // Handle shift-clicking from display case to remove
        if (slot >= DISPLAY_CASE_START && slot <= DISPLAY_CASE_END) {
            int displayIndex = slot - DISPLAY_CASE_START;
            if (displayIndex >= 0 && displayIndex < 8) {
                playerData.setDisplayBadge(displayIndex, null);

                // Send update to server
                if (player.getWorld().isClient) {
                    BadgeClientNetworking.updateBadgeDisplay(displayIndex, null);
                } else {
                    BadgeDataManager.savePlayerData();
                    if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                        BadgeNetworking.broadcastBadgeDataUpdate(serverPlayer);
                    }
                }

                // Refresh the GUI to show changes
                this.setupDisplayCasePreview();
            }
            return ItemStack.EMPTY;
        }

        // Don't allow shift-clicking from player inventory to container
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    // Method to refresh display when data changes from network
    public void refreshDisplay() {
        setupGui();
    }
}
package org.chubby.github.badgebox.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.chubby.github.badgebox.badge.Badge;
import org.chubby.github.badgebox.badge.BadgeManager;
import org.chubby.github.badgebox.items.ModItems;
import org.chubby.github.badgebox.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BadgeEditorScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PlayerEntity player;
    private int currentPage = 0;
    private String currentCategory = "all";
    private static final int BADGES_PER_PAGE = 28; // 4 rows of 7 badges
    private static final int DISPLAY_SLOTS = 8; // Top row for display selection

    // Client-side cache for synced data
    private List<String> cachedOwnedBadges = List.of();
    private List<String> cachedDisplayBadges = List.of();
    private String cachedDisplayName = "";
    private boolean cachedAllowPublicView = false;
    private List<String> cachedCategories = List.of();
    private List<NetworkHandler.BadgeData> cachedBadgeData = List.of();

    public BadgeEditorScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = new SimpleInventory(54);
        this.player = playerInventory.player;

        // Add slots for the GUI
        // Top row (slots 0-8): Display badge selection + category selector
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new BadgeSlot(inventory, i, 8 + i * 18, 18));
        }

        // Main content area (slots 9-44): Badge browser
        for (int i = 1; i < 5; ++i) { // 4 rows
            for (int j = 0; j < 7; ++j) { // 7 columns
                int slot = j + (i-1) * 7 + 9;
                this.addSlot(new BadgeSlot(inventory, slot, 8 + j * 18 + 18, 18 + i * 18));
            }
        }

        // Bottom row (slots 45-53): Navigation and controls
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new BadgeSlot(inventory, i + 45, 8 + i * 18, 18 + 5 * 18));
        }

        // Add player inventory slots
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 103 + i * 18 + 36));
            }
        }

        // Add player hotbar slots
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 161 + 36));
        }

        // Request data from server if on client
        if (player.getWorld().isClient) {
            requestInitialData();
        } else {
            updateDisplay();
        }
    }

    private void requestInitialData() {
        // Request player's badge data
        ClientPlayNetworking.send(new NetworkHandler.RequestPlayerBadgesPayload(player.getUuid()));
        // Request all badge categories and data
        ClientPlayNetworking.send(new NetworkHandler.RequestBadgeCategoriesPayload());
    }

    // Method to be called when player badge data is synced from server
    public void updatePlayerBadgeData(List<String> ownedBadges, List<String> displayBadges, String displayName, boolean allowPublicView) {
        this.cachedOwnedBadges = ownedBadges;
        this.cachedDisplayBadges = displayBadges;
        this.cachedDisplayName = displayName;
        this.cachedAllowPublicView = allowPublicView;
        updateDisplay();
    }

    // Method to be called when badge categories and data are synced from server
    public void updateBadgeCategoriesData(List<String> categories, List<NetworkHandler.BadgeData> badgeData) {
        this.cachedCategories = categories;
        this.cachedBadgeData = badgeData;
        updateDisplay();
    }

    private void updateDisplay() {
        inventory.clear();

        // Fill display selection row (top row)
        fillDisplaySelectionRow(cachedDisplayBadges);

        // Fill badge browser area
        fillBadgeBrowser(cachedOwnedBadges);

        // Fill navigation and controls
        fillNavigationRow();
    }

    private void fillDisplaySelectionRow(List<String> displayBadges) {
        // Slot 0: Category selector
        inventory.setStack(0, createCategoryItem());

        // Slots 1-8: Display badge slots
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            int slot = i + 1;
            if (i < displayBadges.size()) {
                String badgeId = displayBadges.get(i);
                NetworkHandler.BadgeData badgeData = getBadgeDataById(badgeId);
                if (badgeData != null) {
                    ItemStack badgeStack = createBadgeItemStack(badgeData);
                    inventory.setStack(slot, badgeStack);
                }
            } else {
                inventory.setStack(slot, new ItemStack(ModItems.GENERIC_EMPTY_DISPLAY_SLOT));
            }
        }
    }

    private void fillBadgeBrowser(List<String> playerBadges) {
        List<String> filteredBadges = getFilteredBadges(playerBadges);
        int startIndex = currentPage * BADGES_PER_PAGE;

        // Fill main badge browser area (slots 9-36)
        for (int i = 0; i < BADGES_PER_PAGE; i++) {
            int slot = i + 9;
            int badgeIndex = startIndex + i;

            if (badgeIndex < filteredBadges.size()) {
                String badgeId = filteredBadges.get(badgeIndex);
                NetworkHandler.BadgeData badgeData = getBadgeDataById(badgeId);
                if (badgeData != null) {
                    ItemStack badgeStack = createBadgeItemStack(badgeData);
                    inventory.setStack(slot, badgeStack);
                }
            } else {
                inventory.setStack(slot, new ItemStack(ModItems.BADGE_SILHOUETTE));
            }
        }
    }

    private void fillNavigationRow() {
        // Previous page button
        inventory.setStack(45, createNavigationItem("Previous Page", "←"));

        // Page indicator
        List<String> filteredBadges = getFilteredBadges(cachedOwnedBadges);
        int maxPages = Math.max(1, (filteredBadges.size() + BADGES_PER_PAGE - 1) / BADGES_PER_PAGE);
        inventory.setStack(47, createPageIndicator(currentPage + 1, maxPages));

        // Category navigator
        inventory.setStack(48, createCategoryNavigator());

        // Next page button
        inventory.setStack(53, createNavigationItem("Next Page", "→"));

        // Save/Close button
        inventory.setStack(49, createSaveButton());

        // Display name editor
        inventory.setStack(46, createDisplayNameEditor());

        // Privacy toggle
        inventory.setStack(52, createPrivacyToggle());
    }

    private List<String> getFilteredBadges(List<String> playerBadges) {
        if ("all".equals(currentCategory)) {
            return new ArrayList<>(playerBadges);
        }

        List<String> filtered = new ArrayList<>();
        for (String badgeId : playerBadges) {
            NetworkHandler.BadgeData badgeData = getBadgeDataById(badgeId);
            if (badgeData != null && currentCategory.equals(badgeData.category())) {
                filtered.add(badgeId);
            }
        }
        return filtered;
    }

    private NetworkHandler.BadgeData getBadgeDataById(String badgeId) {
        return cachedBadgeData.stream()
                .filter(data -> data.id().equals(badgeId))
                .findFirst()
                .orElse(null);
    }

    private ItemStack createBadgeItemStack(NetworkHandler.BadgeData badgeData) {
        // Create item stack for badge - you might want to use a specific badge item
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6" + badgeData.name()));
        // You can add lore with description and category info
        return item;
    }

    private ItemStack createCategoryItem() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§9Category: §f" + currentCategory));
        return item;
    }

    private ItemStack createPageIndicator(int currentPage, int maxPages) {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§ePage " + currentPage + " / " + maxPages));
        return item;
    }

    private ItemStack createCategoryNavigator() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§bNext Category"));
        return item;
    }

    private ItemStack createSaveButton() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§aSave & Close"));
        return item;
    }

    private ItemStack createDisplayNameEditor() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§dDisplay Name: §f" + cachedDisplayName));
        return item;
    }

    private ItemStack createPrivacyToggle() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        String status = cachedAllowPublicView ? "§aPublic" : "§cPrivate";
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§7Privacy: " + status));
        return item;
    }

    private ItemStack createNavigationItem(String name, String symbol) {
        ItemStack item = new ItemStack(ModItems.NAV_ARROW_LEFT); // or NAV_ARROW_RIGHT
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§f" + symbol + " " + name));
        return item;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < 54) {
            handleSlotClick(slotIndex, button, actionType);
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void handleSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType) {
        if (!player.getWorld().isClient) return; // Only handle on client side

        // Handle display slot clicks (slots 1-8)
        if (slotIndex >= 1 && slotIndex <= 8) {
            handleDisplaySlotClick(slotIndex - 1);
        }
        // Handle badge browser clicks (slots 9-36)
        else if (slotIndex >= 9 && slotIndex <= 36) {
            handleBadgeBrowserClick(slotIndex - 9);
        }
        // Handle navigation clicks (slots 45-53)
        else if (slotIndex >= 45 && slotIndex <= 53) {
            handleNavigationClick(slotIndex);
        }
    }

    private void handleDisplaySlotClick(int displaySlot) {
        // Remove badge from display if clicking on occupied slot
        List<String> displayBadges = new ArrayList<>(cachedDisplayBadges);
        if (displaySlot < displayBadges.size()) {
            displayBadges.remove(displaySlot);
            // Send update to server
            ClientPlayNetworking.send(new NetworkHandler.UpdateDisplayBadgesPayload(displayBadges));
        }
    }

    private void handleBadgeBrowserClick(int browserSlot) {
        List<String> playerBadges = getFilteredBadges(cachedOwnedBadges);
        int badgeIndex = currentPage * BADGES_PER_PAGE + browserSlot;

        if (badgeIndex < playerBadges.size()) {
            String badgeId = playerBadges.get(badgeIndex);
            List<String> displayBadges = new ArrayList<>(cachedDisplayBadges);

            // Add to display if not already there and there's space
            if (!displayBadges.contains(badgeId) && displayBadges.size() < DISPLAY_SLOTS) {
                displayBadges.add(badgeId);
                // Send update to server
                ClientPlayNetworking.send(new NetworkHandler.UpdateDisplayBadgesPayload(displayBadges));
            }
        }
    }

    private void handleNavigationClick(int slotIndex) {
        switch (slotIndex) {
            case 45: // Previous page
                if (currentPage > 0) {
                    currentPage--;
                    updateDisplay();
                }
                break;
            case 46: // Display name editor
                // For now, just show current name. In a full implementation,
                // you'd want to open a text input dialog
                player.sendMessage(Text.literal("§bCurrent display name: §f" + cachedDisplayName), false);
                player.sendMessage(Text.literal("§7Use chat to change: /badge displayname <name>"), false);
                break;
            case 48: // Category navigator
                cycleCategorySelection();
                break;
            case 49: // Save/Close
                handleSaveAndClose();
                break;
            case 52: // Privacy toggle
                handlePrivacyToggle();
                break;
            case 53: // Next page
                List<String> filteredBadges = getFilteredBadges(cachedOwnedBadges);
                int maxPages = Math.max(1, (filteredBadges.size() + BADGES_PER_PAGE - 1) / BADGES_PER_PAGE);
                if (currentPage < maxPages - 1) {
                    currentPage++;
                    updateDisplay();
                }
                break;
        }
    }

    private void cycleCategorySelection() {
        List<String> categories = new ArrayList<>(cachedCategories);
        categories.add(0, "all"); // Add "all" option at the beginning

        int currentIndex = categories.indexOf(currentCategory);
        currentIndex = (currentIndex + 1) % categories.size();
        currentCategory = categories.get(currentIndex);
        currentPage = 0; // Reset to first page when changing category
        updateDisplay();
    }

    private void handleSaveAndClose() {
        if(player instanceof ServerPlayerEntity){
            NetworkHandler.syncAllPlayerBadges((ServerPlayerEntity) player);
        }
    }
    private void handlePrivacyToggle() {
        if(player instanceof ServerPlayerEntity){
            NetworkHandler.syncAllPlayerBadges((ServerPlayerEntity) player);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY; // Disable shift-click for now
    }

    // Custom slot class to handle badge interactions
    private static class BadgeSlot extends Slot {
        public BadgeSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false; // Prevent manual insertion
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return false; // Prevent manual removal
        }
    }
}
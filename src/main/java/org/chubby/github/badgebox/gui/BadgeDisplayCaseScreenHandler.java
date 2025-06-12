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
import net.minecraft.text.Text;
import org.chubby.github.badgebox.badge.Badge;
import org.chubby.github.badgebox.badge.BadgeManager;
import org.chubby.github.badgebox.items.ModItems;
import org.chubby.github.badgebox.network.NetworkHandler;

import java.util.List;
import java.util.UUID;

public class BadgeDisplayCaseScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PlayerEntity viewer;
    private final UUID displayPlayerUuid;
    private final String displayPlayerName;

    // Client-side cache for synced data
    private List<String> cachedOwnedBadges = List.of();
    private List<String> cachedDisplayBadges = List.of();
    private String cachedDisplayName = "";
    private boolean cachedAllowPublicView = false;

    // Constructor for viewing own display case
    public BadgeDisplayCaseScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, playerInventory.player.getUuid(), playerInventory.player.getName().getString());
    }

    // Constructor for viewing another player's display case
    public BadgeDisplayCaseScreenHandler(int syncId, PlayerInventory playerInventory, UUID displayPlayerUuid, String displayPlayerName) {
        super(ScreenHandlerType.GENERIC_9X4, syncId);
        this.inventory = new SimpleInventory(36);
        this.viewer = playerInventory.player;
        this.displayPlayerUuid = displayPlayerUuid;
        this.displayPlayerName = displayPlayerName;

        // Add slots for the display case GUI (4 rows of 9 slots)
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new DisplaySlot(inventory, j + i * 9, 8 + j * 18, 18 + i * 18));
            }
        }

        // Add player inventory slots (only for viewer)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 103 + i * 18 + 18));
            }
        }

        // Add player hotbar slots
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 161 + 18));
        }

        // Request player badge data from server
        if (viewer.getWorld().isClient) {
            requestPlayerBadgeData();
        } else {
            // Server-side: initialize with current data
            updateDisplay();
        }
    }

    private void requestPlayerBadgeData() {
        // Send packet to request badge data for the display player
        ClientPlayNetworking.send(new NetworkHandler.RequestPlayerBadgesPayload(displayPlayerUuid));
    }

    // Method to be called when badge data is synced from server
    public void updateBadgeData(List<String> ownedBadges, List<String> displayBadges, String displayName, boolean allowPublicView) {
        this.cachedOwnedBadges = ownedBadges;
        this.cachedDisplayBadges = displayBadges;
        this.cachedDisplayName = displayName;
        this.cachedAllowPublicView = allowPublicView;
        updateDisplay();
    }

    private void updateDisplay() {
        inventory.clear();

        // Create title/header area (row 0)
        fillHeaderRow();

        // Display the player's selected badges (row 1)
        fillBadgeDisplayRow();

        // Fill decorative/info rows (rows 2-3)
        fillInfoRows();
    }

    private void fillHeaderRow() {
        // Player head or name indicator in center
        ItemStack playerInfo = createPlayerInfoItem();
        inventory.setStack(4, playerInfo); // Center slot of first row

        // Decorative border items
        for (int i = 0; i < 9; i++) {
            if (i != 4) { // Don't overwrite the player info
                inventory.setStack(i, new ItemStack(ModItems.BADGE_CATEGORY_ITEM));
            }
        }
    }

    private void fillBadgeDisplayRow() {
        List<String> displayBadges = cachedDisplayBadges;

        // Center the badges in the row (start from slot 1 if 8 badges to center them)
        int startSlot = 9 + (9 - Math.min(displayBadges.size(), 8)) / 2;

        for (int i = 0; i < 8; i++) {
            int slot = startSlot + i;
            if (slot >= 9 && slot <= 17) { // Ensure we stay in row 1
                if (i < displayBadges.size()) {
                    String badgeId = displayBadges.get(i);
                    Badge badge = BadgeManager.getBadge(badgeId);
                    if (badge != null) {
                        ItemStack badgeStack = badge.createItemStack();
                        inventory.setStack(slot, badgeStack);
                    }
                } else {
                    inventory.setStack(slot, new ItemStack(ModItems.GENERIC_EMPTY_DISPLAY_SLOT));
                }
            }
        }

        // Fill empty slots in the badge row with decorative items
        for (int i = 9; i < 18; i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, new ItemStack(ModItems.BADGE_SILHOUETTE_IN_CASE));
            }
        }
    }

    private void fillInfoRows() {
        // Statistics and info in rows 2-3
        fillStatisticsRow(); // Row 2 (slots 18-26)
        fillActionRow();     // Row 3 (slots 27-35)
    }

    private void fillStatisticsRow() {
        List<String> allBadges = cachedOwnedBadges;
        List<String> displayBadges = cachedDisplayBadges;

        // Total badges owned
        ItemStack totalBadgesInfo = createStatisticItem("Total Badges", String.valueOf(allBadges.size()));
        inventory.setStack(20, totalBadgesInfo);

        // Badges displayed
        ItemStack displayedBadgesInfo = createStatisticItem("Displayed", String.valueOf(displayBadges.size()));
        inventory.setStack(22, displayedBadgesInfo);

        // Categories
        long categories = allBadges.stream()
                .map(BadgeManager::getBadge)
                .filter(badge -> badge != null)
                .map(Badge::getCategory)
                .distinct()
                .count();
        ItemStack categoriesInfo = createStatisticItem("Categories", String.valueOf(categories));
        inventory.setStack(24, categoriesInfo);

        // Fill other slots with decorative items
        for (int i = 18; i < 27; i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, new ItemStack(ModItems.BADGE_SILHOUETTE));
            }
        }
    }

    private void fillActionRow() {
        // Action buttons in bottom row
        if (isOwnDisplayCase()) {
            // Edit button for own display case
            ItemStack editButton = createActionItem("Edit Display", "Click to edit your badge display");
            inventory.setStack(31, editButton);
        } else {
            // Share/Link button for other players' cases
            ItemStack shareButton = createActionItem("Share", "Get link to share this display");
            inventory.setStack(31, shareButton);
        }

        // Close button
        ItemStack closeButton = createActionItem("Close", "Close this display");
        inventory.setStack(35, closeButton);

        // Privacy status indicator
        if (isOwnDisplayCase()) {
            ItemStack privacyButton = createActionItem(
                    cachedAllowPublicView ? "Public" : "Private",
                    "Click to toggle privacy settings"
            );
            inventory.setStack(29, privacyButton);
        }

        // Fill other slots with decorative items
        for (int i = 27; i < 36; i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, new ItemStack(ModItems.BADGE_SILHOUETTE));
            }
        }
    }

    private ItemStack createPlayerInfoItem() {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        String displayName = cachedDisplayName.isEmpty() ? displayPlayerName : cachedDisplayName;
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(displayName + "'s Badge Display"));
        return item;
    }

    private ItemStack createStatisticItem(String label, String value) {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6" + label + ": §f" + value));
        return item;
    }

    private ItemStack createActionItem(String action, String description) {
        ItemStack item = new ItemStack(ModItems.BADGE_CATEGORY_ITEM);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§a" + action));
        // You can add lore with description using LORE component
        return item;
    }

    private boolean isOwnDisplayCase() {
        return viewer.getUuid().equals(displayPlayerUuid);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < 36) {
            handleSlotClick(slotIndex, button, actionType);
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void handleSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType) {
        // Handle action buttons (row 3, slots 27-35)
        if (slotIndex >= 27 && slotIndex <= 35) {
            handleActionClick(slotIndex);
        }
        // Badge clicks for viewing details (row 1, slots 9-17)
        else if (slotIndex >= 9 && slotIndex <= 17) {
            handleBadgeClick(slotIndex - 9);
        }
    }

    private void handleActionClick(int slotIndex) {
        if (!viewer.getWorld().isClient) return; // Only handle on client side

        switch (slotIndex) {
            case 29: // Privacy toggle (only for own display case)
                if (isOwnDisplayCase()) {
                    handlePrivacyToggle();
                }
                break;
            case 31: // Edit/Share button
                if (isOwnDisplayCase()) {
                    handleEditRequest();
                } else {
                    handleShareRequest();
                }
                break;
            case 35: // Close button
                handleCloseRequest();
                break;
        }
    }

    private void handleBadgeClick(int badgeSlot) {
        List<String> displayBadges = cachedDisplayBadges;

        // Calculate actual badge index based on centered display
        int startIndex = (8 - Math.min(displayBadges.size(), 8)) / 2;
        int badgeIndex = badgeSlot - startIndex;

        if (badgeIndex >= 0 && badgeIndex < displayBadges.size()) {
            String badgeId = displayBadges.get(badgeIndex);
            Badge badge = BadgeManager.getBadge(badgeId);
            if (badge != null) {
                // Show badge details to viewer
                showBadgeDetails(badge);
            }
        }
    }

    private void showBadgeDetails(Badge badge) {
        // Send badge information to the viewer via chat
        Text message = Text.literal("§6" + badge.getName() + "§r: " + badge.getDescription());
        viewer.sendMessage(message, false);
    }

    private void handleEditRequest() {
        // Send packet to request badge editor GUI
        ClientPlayNetworking.send(new NetworkHandler.OpenBadgeGuiPayload());
    }

    private void handleShareRequest() {
        // Generate shareable link or command
        String shareCommand = "/badge display " + displayPlayerName;
        Text shareMessage = Text.literal("§aShare this display: §f" + shareCommand);
        viewer.sendMessage(shareMessage, false);
    }

    private void handlePrivacyToggle() {
        // Send packet to toggle privacy setting
        ClientPlayNetworking.send(new NetworkHandler.TogglePublicViewPayload(!cachedAllowPublicView));
    }

    private void handleCloseRequest() {
        // Close the GUI - handled by client typically
        viewer.currentScreenHandler = viewer.playerScreenHandler;
        //viewer.closeHandledScreen();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY; // Disable shift-click
    }

    // Custom slot class for display case
    private static class DisplaySlot extends Slot {
        public DisplaySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false; // Read-only display
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return false; // Read-only display
        }
    }
}
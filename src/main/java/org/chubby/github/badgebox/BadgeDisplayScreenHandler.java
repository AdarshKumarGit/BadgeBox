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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.client.BadgeClientNetworking;
import java.util.List;

public class BadgeDisplayScreenHandler extends GenericContainerScreenHandler {
    private final PlayerEntity player;
    private PlayerBadgeData displayData;
    private final boolean isOwnDisplay;
    private final java.util.UUID targetPlayerId;

    public BadgeDisplayScreenHandler(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        this(syncId, playerInventory, player, BadgeDataManager.getPlayerData(player.getUuid()));
    }

    public BadgeDisplayScreenHandler(int syncId, PlayerInventory playerInventory, PlayerEntity player, PlayerBadgeData displayData) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(27), 3);
        this.player = player;
        this.displayData = displayData;
        this.targetPlayerId = displayData.getPlayerId();
        this.isOwnDisplay = displayData.getPlayerId().equals(player.getUuid());

        // Request fresh data from server if on client
        if (player.getWorld().isClient && !isOwnDisplay) {
            BadgeClientNetworking.requestBadgeDataSync(targetPlayerId);
        }

        setupDisplay();
    }

    private void setupDisplay() {
        Inventory inventory = getInventory();

        // Clear inventory first
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        List<Identifier> displayBadges = displayData.getDisplayBadges();

        // Display case in center area (slots 10-17)
        for (int i = 0; i < Math.min(8, displayBadges.size()); i++) {
            int slot = 10 + i;
            Identifier badgeId = displayBadges.get(i);

            if (badgeId != null) {
                Badge badge = BadgeRegistry.getBadge(badgeId);
                if (badge != null) {
                    ItemStack displayItem = badge.getDisplayItem();
                    displayItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§e" + badge.getName()));

                    // Add lore
                    List<Text> lore = badge.getLore().stream()
                            .map(Text::literal)
                            .collect(java.util.stream.Collectors.toList());

                    // Add additional context
                    lore.add(Text.literal(""));
                    lore.add(Text.literal("§7Category: §f" + badge.getCategory().replace("_", " ")));
                    if (!isOwnDisplay) {
                        lore.add(Text.literal("§7This badge belongs to §f" + getDisplayName()));
                    }

                    displayItem.set(DataComponentTypes.LORE, new LoreComponent(lore));
                    inventory.setStack(slot, displayItem);
                }
            } else {
                // Empty display slot
                ItemStack emptySlot = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                emptySlot.set(DataComponentTypes.ITEM_NAME, Text.literal("§7Empty Display Slot"));

                if (isOwnDisplay) {
                    emptySlot.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§7Use the Badge Editor to"),
                            Text.literal("§7customize your display")
                    )));
                }

                inventory.setStack(slot, emptySlot);
            }
        }

        // Add decorative border
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.ITEM_NAME, Text.literal(""));

        // Top and bottom borders
        for (int i = 0; i < 9; i++) {
            if (i < 1 || i > 8) { // Don't cover the display area
                inventory.setStack(i, border.copy()); // Top row
                inventory.setStack(18 + i, border.copy()); // Bottom row
            }
        }

        // Side borders
        inventory.setStack(9, border.copy());
        inventory.setStack(17, border.copy());

        // Player info and controls
        setupPlayerInfo();
    }

    private void setupPlayerInfo() {
        Inventory inventory = getInventory();

        // Player info item
        ItemStack infoItem = new ItemStack(Items.PLAYER_HEAD);
        String displayName = getDisplayName();

        if (isOwnDisplay) {
            infoItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§6Your Badge Display"));
            infoItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7Badges Owned: §f" + displayData.getOwnedBadges().size()),
                    Text.literal("§7Badges Displayed: §f" + getDisplayedBadgeCount()),
                    Text.literal(""),
                    Text.literal("§7Press §eB §7to open Badge Editor")
            )));
        } else {
            infoItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§6" + displayName + "'s Badge Display"));
            infoItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7Badges Displayed: §f" + getDisplayedBadgeCount()),
                    Text.literal(""),
                    Text.literal("§7Viewing another player's badges")
            )));
        }

        inventory.setStack(4, infoItem);

        // Add refresh button for non-own displays
        if (!isOwnDisplay) {
            ItemStack refreshItem = new ItemStack(Items.COMPASS);
            refreshItem.set(DataComponentTypes.ITEM_NAME, Text.literal("§aRefresh Display"));
            refreshItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7Click to refresh badge data"),
                    Text.literal("§7from the server")
            )));
            inventory.setStack(8, refreshItem);
        }
    }

    private String getDisplayName() {
        String displayName = displayData.getDisplayName();
        return displayName.isEmpty() ? "Player" : displayName;
    }

    private int getDisplayedBadgeCount() {
        return (int) displayData.getDisplayBadges().stream()
                .filter(badgeId -> badgeId != null)
                .count();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        // Handle refresh button click
        if (slotIndex == 8 && !isOwnDisplay) {
            if (player.getWorld().isClient) {
                BadgeClientNetworking.requestBadgeDataSync(targetPlayerId);
            }
            return;
        }

        // Prevent any other interactions
        if (slotIndex >= 0 && slotIndex < 27) {
            return;
        }

        // Allow normal inventory interactions
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // Prevent shift-clicking items out of display
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    // Method to update display data from network
    public void updateDisplayData(PlayerBadgeData newData) {
        if (newData.getPlayerId().equals(this.targetPlayerId)) {
            this.displayData = newData;
            setupDisplay();
        }
    }

    // Method to refresh display
    public void refreshDisplay() {
        setupDisplay();
    }
}
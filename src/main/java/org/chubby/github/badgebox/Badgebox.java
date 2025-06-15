package org.chubby.github.badgebox;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Badgebox implements ModInitializer {
    public static final String MOD_ID = "badgebox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Items
    public static final Item BADGE_BOX = new BadgeBoxItem(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Badge Box Mod");

        // Register items
        registerItems();

        // Initialize networking
        BadgeNetworking.registerPayloads();
        BadgeNetworking.registerServerHandlers();

        // Initialize components
        BadgeRegistry.init();
        BadgeCommands.register();

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            BadgeDataManager.init(server);
            BadgeDataManager.loadBadges();
            BadgeDataManager.loadPlayerData();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BadgeDataManager.saveBadges();
        });

        // Register player join event to sync badge data
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // You might want to add player join/leave events here
            registerPlayerEvents();
        });
    }

    private void registerItems() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "badge_box"), BADGE_BOX);

        // Add to creative inventory
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(BADGE_BOX);
        });
    }

    private void registerPlayerEvents() {
        // You can add player join/leave events here if needed
        // For example, to sync badge data when players join
    }
}
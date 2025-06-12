package org.chubby.github.badgebox;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.commands.BadgeCommand;
import org.chubby.github.badgebox.config.BadgeConfig;
import org.chubby.github.badgebox.items.ModItems;
import org.chubby.github.badgebox.network.NetworkHandler;
import org.chubby.github.badgebox.storage.BadgeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeBox implements ModInitializer {
    public static final String MOD_ID = "badgebox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Badge Box Mod");
        ModItems.init();
        BadgeConfig.init();
        BadgeStorage.initialize(BadgeStorage.StorageType.FILE," ");
        NetworkHandler.init();

        // Register commands
        CommandRegistrationCallback.EVENT.register(BadgeCommand::register);

        LOGGER.info("Badge Box Mod initialized successfully");
    }

    public static Identifier id(String path) {
        return  Identifier.of(MOD_ID, path);
    }
}
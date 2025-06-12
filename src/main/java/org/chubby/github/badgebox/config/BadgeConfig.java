package org.chubby.github.badgebox.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.chubby.github.badgebox.BadgeBox;
import org.chubby.github.badgebox.badge.Badge;
import org.chubby.github.badgebox.badge.BadgeManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class BadgeConfig {
    private static final Gson GSON = new Gson();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("badgebox");
    private static final Path BADGES_CONFIG = CONFIG_DIR.resolve("badges.json");

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadBadges();
        } catch (IOException e) {
            BadgeBox.LOGGER.error("Failed to initialize badge config", e);
        }
    }

    private static void loadBadges() {
        if (!Files.exists(BADGES_CONFIG)) {
            createDefaultConfig();
            return;
        }

        try (FileReader reader = new FileReader(BADGES_CONFIG.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray badgesArray = root.getAsJsonArray("badges");

            for (int i = 0; i < badgesArray.size(); i++) {
                JsonObject badgeObj = badgesArray.get(i).getAsJsonObject();
                Badge badge = Badge.fromJson(badgeObj);
                BadgeManager.registerBadge(badge);
            }

            BadgeBox.LOGGER.info("Loaded {} badges from config", badgesArray.size());
        } catch (IOException e) {
            BadgeBox.LOGGER.error("Failed to load badges config", e);
        }
    }

    private static void createDefaultConfig() {
        try (FileWriter writer = new FileWriter(BADGES_CONFIG.toFile())) {
            JsonObject root = new JsonObject();
            JsonArray badgesArray = new JsonArray();

            // Add some example badges
            String[][] defaultBadges = {
                    {"first_join", "First Steps", "General", "Joined the server for the first time", "badgebox:textures/badges/first_join.png"},
                    {"veteran", "Veteran", "General", "Been on the server for over a year", "badgebox:textures/badges/veteran.png"},
                    {"builder", "Master Builder", "Building", "Built something amazing", "badgebox:textures/badges/builder.png"},
                    {"explorer", "Explorer", "Adventure", "Discovered new lands", "badgebox:textures/badges/explorer.png"}
            };

            for (String[] badgeData : defaultBadges) {
                JsonObject badgeObj = new JsonObject();
                badgeObj.addProperty("id", badgeData[0]);
                badgeObj.addProperty("name", badgeData[1]);
                badgeObj.addProperty("category", badgeData[2]);
                badgeObj.addProperty("description", badgeData[3]);
                badgeObj.addProperty("texture", badgeData[4]);
                badgeObj.addProperty("obtainable", true);
                badgesArray.add(badgeObj);
            }

            root.add("badges", badgesArray);
            GSON.toJson(root, writer);

            BadgeBox.LOGGER.info("Created default badges config");
        } catch (IOException e) {
            BadgeBox.LOGGER.error("Failed to create default config", e);
        }
    }
}
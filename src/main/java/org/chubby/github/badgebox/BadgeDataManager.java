package org.chubby.github.badgebox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BadgeDataManager");
    private static final Map<UUID, PlayerBadgeData> playerData = new ConcurrentHashMap<>();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("badgebox");
    private static final Path BADGES_FILE = CONFIG_DIR.resolve("badges.json");
    private static final Path PLAYER_DATA_FILE = CONFIG_DIR.resolve("player_data.dat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MinecraftServer server;

    public static void init(MinecraftServer server) {
        BadgeDataManager.server = server;
        createConfigDirectory();
    }

    private static void createConfigDirectory() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    public static PlayerBadgeData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, PlayerBadgeData::new);
    }

    public static void savePlayerData() {
        if (server == null) return;

        try {
            NbtCompound rootNbt = new NbtCompound();
            RegistryWrapper.WrapperLookup wrapperLookup = server.getRegistryManager();

            for (Map.Entry<UUID, PlayerBadgeData> entry : playerData.entrySet()) {
                rootNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
            }

            NbtIo.writeCompressed(rootNbt, PLAYER_DATA_FILE);
            LOGGER.info("Saved player badge data for {} players", playerData.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save player data", e);
        }
    }

    public static void loadPlayerData() {
        if (!Files.exists(PLAYER_DATA_FILE)) {
            LOGGER.info("No player data file found, starting fresh");
            return;
        }

        try {
            NbtCompound rootNbt = NbtIo.readCompressed(PLAYER_DATA_FILE, NbtSizeTracker.ofUnlimitedBytes());

            for (String key : rootNbt.getKeys()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    PlayerBadgeData data = PlayerBadgeData.fromNbt(rootNbt.getCompound(key));
                    playerData.put(playerId, data);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid UUID in player data: {}", key);
                }
            }

            LOGGER.info("Loaded player badge data for {} players", playerData.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load player data", e);
        }
    }

    public static void loadBadges() {
        BadgeRegistry.clearBadges();

        if (!Files.exists(BADGES_FILE)) {
            createDefaultBadgesConfig();
        }

        loadBadgesFromConfig();
    }

    public static void saveBadges() {
        savePlayerData();
    }

    private static void createDefaultBadgesConfig() {
        try {
            JsonObject config = new JsonObject();
            config.addProperty("version", "1.0");

            // Create default badges
            JsonObject badges = new JsonObject();

            // Gym badges
            badges.add("boulder_badge", createBadgeJson("Boulder Badge",
                    Arrays.asList("§7The first gym badge", "§7Obtained from Brock"),
                    "gym", "minecraft:cobblestone", "minecraft:gray_concrete"));

            badges.add("cascade_badge", createBadgeJson("Cascade Badge",
                    Arrays.asList("§7The second gym badge", "§7Obtained from Misty"),
                    "gym", "minecraft:prismarine_shard", "minecraft:blue_concrete"));

            badges.add("thunder_badge", createBadgeJson("Thunder Badge",
                    Arrays.asList("§7The third gym badge", "§7Obtained from Lt. Surge"),
                    "gym", "minecraft:gold_ingot", "minecraft:yellow_concrete"));

            // Elite Four badges
            badges.add("lorelei_badge", createBadgeJson("Lorelei Badge",
                    Arrays.asList("§7Elite Four member defeated", "§7Ice type specialist"),
                    "elite_four", "minecraft:ice", "minecraft:light_blue_concrete"));

            badges.add("bruno_badge", createBadgeJson("Bruno Badge",
                    Arrays.asList("§7Elite Four member defeated", "§7Fighting type specialist"),
                    "elite_four", "minecraft:brick", "minecraft:brown_concrete"));

            // Champion badge
            badges.add("champion_badge", createBadgeJson("Champion Badge",
                    Arrays.asList("§6The ultimate achievement", "§6Pokémon Champion!"),
                    "champion", "minecraft:diamond", "minecraft:purple_concrete"));

            // Special badges
            badges.add("shiny_hunter", createBadgeJson("Shiny Hunter",
                    Arrays.asList("§5Caught a shiny Pokémon", "§5Extremely rare!"),
                    "special", "minecraft:nether_star", "minecraft:magenta_concrete"));

            config.add("badges", badges);

            try (FileWriter writer = new FileWriter(BADGES_FILE.toFile())) {
                GSON.toJson(config, writer);
            }

            LOGGER.info("Created default badges configuration");
        } catch (IOException e) {
            LOGGER.error("Failed to create default badges config", e);
        }
    }

    private static JsonObject createBadgeJson(String name, List<String> lore, String category,
                                              String displayItem, String silhouetteItem) {
        JsonObject badge = new JsonObject();
        badge.addProperty("name", name);
        badge.addProperty("category", category);
        badge.addProperty("display_item", displayItem);
        badge.addProperty("silhouette_item", silhouetteItem);
        badge.addProperty("obtainable", true);

        JsonObject loreObj = new JsonObject();
        for (int i = 0; i < lore.size(); i++) {
            loreObj.addProperty(String.valueOf(i), lore.get(i));
        }
        badge.add("lore", loreObj);

        return badge;
    }

    private static void loadBadgesFromConfig() {
        try (FileReader reader = new FileReader(BADGES_FILE.toFile())) {
            JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject badges = config.getAsJsonObject("badges");

            if (server == null) {
                LOGGER.warn("Server is null, cannot load badges");
                return;
            }

            RegistryWrapper.WrapperLookup wrapperLookup = server.getRegistryManager();

            for (Map.Entry<String, com.google.gson.JsonElement> entry : badges.entrySet()) {
                try {
                    String badgeId = entry.getKey();
                    JsonObject badgeData = entry.getValue().getAsJsonObject();

                    Badge badge = createBadgeFromJson(badgeId, badgeData, wrapperLookup);
                    BadgeRegistry.registerBadge(badge);
                } catch (Exception e) {
                    LOGGER.error("Failed to load badge: {}", entry.getKey(), e);
                }
            }

            LOGGER.info("Loaded {} badges from configuration", badges.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load badges configuration", e);
        }
    }

    private static Badge createBadgeFromJson(String badgeId, JsonObject data,
                                             RegistryWrapper.WrapperLookup wrapperLookup) {
        Identifier id = Identifier.of("badgebox", badgeId);
        String name = data.get("name").getAsString();
        String category = data.get("category").getAsString();
        boolean obtainable = data.has("obtainable") ? data.get("obtainable").getAsBoolean() : true;

        // Load lore
        List<String> lore = new ArrayList<>();
        if (data.has("lore")) {
            JsonObject loreObj = data.getAsJsonObject("lore");
            int i = 0;
            while (loreObj.has(String.valueOf(i))) {
                lore.add(loreObj.get(String.valueOf(i)).getAsString());
                i++;
            }
        }

        // Create display item
        ItemStack displayItem = createItemFromString(data.get("display_item").getAsString());
        ItemStack silhouetteItem = createItemFromString(data.get("silhouette_item").getAsString());

        return new Badge(id, name, lore, category, displayItem, silhouetteItem, obtainable);
    }

    private static ItemStack createItemFromString(String itemString) {
        try {
            Identifier itemId = Identifier.of(itemString);
            return new ItemStack(Registries.ITEM.get(itemId));
        } catch (Exception e) {
            LOGGER.warn("Invalid item identifier: {}, using stone as fallback", itemString);
            return new ItemStack(Items.STONE);
        }
    }

    public static Collection<PlayerBadgeData> getAllPlayerData() {
        return new ArrayList<>(playerData.values());
    }

    public static void removePlayerData(UUID playerId) {
        playerData.remove(playerId);
    }

    public static int getTotalPlayersWithBadges() {
        return (int) playerData.values().stream()
                .filter(data -> !data.getOwnedBadges().isEmpty())
                .count();
    }

    public static Map<Identifier, Integer> getBadgeStatistics() {
        Map<Identifier, Integer> stats = new HashMap<>();

        for (PlayerBadgeData data : playerData.values()) {
            for (Identifier badgeId : data.getOwnedBadges()) {
                stats.put(badgeId, stats.getOrDefault(badgeId, 0) + 1);
            }
        }

        return stats;
    }
}
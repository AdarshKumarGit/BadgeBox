package org.chubby.github.badgebox.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.chubby.github.badgebox.BadgeBox;
import org.chubby.github.badgebox.BadgeBox;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeStorage {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, PlayerBadgeData> playerDataCache = new ConcurrentHashMap<>();

    // Configuration
    private static StorageType storageType = StorageType.FILE;
    private static String databaseUrl = "jdbc:sqlite:badgebox.db";
    private static Path dataDirectory = Paths.get("badgebox");
    private static Path playerDataFile = dataDirectory.resolve("player_data.json");

    // Database connection (if using SQL)
    private static Connection dbConnection;

    public enum StorageType {
        FILE, SQLITE, MYSQL
    }

    public static class PlayerBadgeData {
        private final Set<String> ownedBadges;
        private final List<String> displayBadges;
        private String displayName;
        private boolean allowPublicView;

        public PlayerBadgeData() {
            this.ownedBadges = new HashSet<>();
            this.displayBadges = new ArrayList<>();
            this.displayName = "";
            this.allowPublicView = true;
        }

        public PlayerBadgeData(Set<String> ownedBadges, List<String> displayBadges, String displayName, boolean allowPublicView) {
            this.ownedBadges = new HashSet<>(ownedBadges);
            this.displayBadges = new ArrayList<>(displayBadges);
            this.displayName = displayName;
            this.allowPublicView = allowPublicView;
        }

        // Getters and setters
        public Set<String> getOwnedBadges() { return new HashSet<>(ownedBadges); }
        public List<String> getDisplayBadges() { return new ArrayList<>(displayBadges); }
        public String getDisplayName() { return displayName; }
        public boolean isAllowPublicView() { return allowPublicView; }

        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setAllowPublicView(boolean allowPublicView) { this.allowPublicView = allowPublicView; }

        public void addBadge(String badgeId) { ownedBadges.add(badgeId); }
        public void removeBadge(String badgeId) {
            ownedBadges.remove(badgeId);
            displayBadges.remove(badgeId);
        }

        public void setDisplayBadges(List<String> badges) {
            displayBadges.clear();
            // Only add badges that the player actually owns
            for (String badgeId : badges) {
                if (ownedBadges.contains(badgeId) && displayBadges.size() < 8) {
                    displayBadges.add(badgeId);
                }
            }
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();

            JsonArray ownedArray = new JsonArray();
            for (String badge : ownedBadges) {
                ownedArray.add(badge);
            }
            json.add("owned", ownedArray);

            JsonArray displayArray = new JsonArray();
            for (String badge : displayBadges) {
                displayArray.add(badge);
            }
            json.add("display", displayArray);

            json.addProperty("displayName", displayName);
            json.addProperty("allowPublicView", allowPublicView);

            return json;
        }

        public static PlayerBadgeData fromJson(JsonObject json) {
            PlayerBadgeData data = new PlayerBadgeData();

            if (json.has("owned")) {
                JsonArray ownedArray = json.getAsJsonArray("owned");
                for (JsonElement element : ownedArray) {
                    data.ownedBadges.add(element.getAsString());
                }
            }

            if (json.has("display")) {
                JsonArray displayArray = json.getAsJsonArray("display");
                for (JsonElement element : displayArray) {
                    data.displayBadges.add(element.getAsString());
                }
            }

            if (json.has("displayName")) {
                data.displayName = json.get("displayName").getAsString();
            }

            if (json.has("allowPublicView")) {
                data.allowPublicView = json.get("allowPublicView").getAsBoolean();
            }

            return data;
        }
    }

    public static void initialize(StorageType type, String dbUrl) {
        storageType = type;
        if (dbUrl != null && !dbUrl.isEmpty()) {
            databaseUrl = dbUrl;
        }

        switch (storageType) {
            case FILE:
                initializeFileStorage();
                break;
            case SQLITE:
            case MYSQL:
                initializeDatabaseStorage();
                break;
        }

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
    }

    private static void initializeFileStorage() {
        try {
            Files.createDirectories(dataDirectory);
            loadFileData();
        } catch (IOException e) {
            BadgeBox.LOGGER.error("Failed to initialize file storage", e);
        }
    }

    private static void initializeDatabaseStorage() {
        try {
            dbConnection = DriverManager.getConnection(databaseUrl);
            createTables();
            loadDatabaseData();
        } catch (SQLException e) {
            BadgeBox.LOGGER.error("Failed to initialize database storage", e);
            // Fallback to file storage
            storageType = StorageType.FILE;
            initializeFileStorage();
        }
    }

    private static void createTables() throws SQLException {
        String createPlayerDataTable = """
            CREATE TABLE IF NOT EXISTS player_badge_data (
                player_uuid TEXT PRIMARY KEY,
                display_name TEXT,
                allow_public_view BOOLEAN DEFAULT TRUE
            )
        """;

        String createOwnedBadgesTable = """
            CREATE TABLE IF NOT EXISTS player_owned_badges (
                player_uuid TEXT,
                badge_id TEXT,
                granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, badge_id),
                FOREIGN KEY (player_uuid) REFERENCES player_badge_data(player_uuid)
            )
        """;

        String createDisplayBadgesTable = """
            CREATE TABLE IF NOT EXISTS player_display_badges (
                player_uuid TEXT,
                badge_id TEXT,
                display_order INTEGER,
                PRIMARY KEY (player_uuid, badge_id),
                FOREIGN KEY (player_uuid) REFERENCES player_badge_data(player_uuid)
            )
        """;

        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(createPlayerDataTable);
            stmt.execute(createOwnedBadgesTable);
            stmt.execute(createDisplayBadgesTable);
        }
    }

    private static void loadFileData() {
        if (Files.exists(playerDataFile)) {
            try {
                String content = Files.readString(playerDataFile);
                JsonObject rootObject = GSON.fromJson(content, JsonObject.class);

                for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    PlayerBadgeData data = PlayerBadgeData.fromJson(entry.getValue().getAsJsonObject());
                    playerDataCache.put(playerId, data);
                }
            } catch (Exception e) {
                BadgeBox.LOGGER.error("Failed to load player data from file", e);
            }
        }
    }

    private static void loadDatabaseData() {
        try {
            String query = """
                SELECT pbd.player_uuid, pbd.display_name, pbd.allow_public_view,
                       GROUP_CONCAT(pob.badge_id) as owned_badges,
                       GROUP_CONCAT(pdb.badge_id ORDER BY pdb.display_order) as display_badges
                FROM player_badge_data pbd
                LEFT JOIN player_owned_badges pob ON pbd.player_uuid = pob.player_uuid
                LEFT JOIN player_display_badges pdb ON pbd.player_uuid = pdb.player_uuid
                GROUP BY pbd.player_uuid
            """;

            try (PreparedStatement stmt = dbConnection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                    String displayName = rs.getString("display_name");
                    boolean allowPublicView = rs.getBoolean("allow_public_view");

                    Set<String> ownedBadges = new HashSet<>();
                    String ownedBadgesStr = rs.getString("owned_badges");
                    if (ownedBadgesStr != null && !ownedBadgesStr.isEmpty()) {
                        ownedBadges.addAll(Arrays.asList(ownedBadgesStr.split(",")));
                    }

                    List<String> displayBadges = new ArrayList<>();
                    String displayBadgesStr = rs.getString("display_badges");
                    if (displayBadgesStr != null && !displayBadgesStr.isEmpty()) {
                        displayBadges.addAll(Arrays.asList(displayBadgesStr.split(",")));
                    }

                    PlayerBadgeData data = new PlayerBadgeData(ownedBadges, displayBadges, displayName, allowPublicView);
                    playerDataCache.put(playerId, data);
                }
            }
        } catch (SQLException e) {
            BadgeBox.LOGGER.error("Failed to load data from database", e);
        }
    }

    // Public API methods
    public static CompletableFuture<Void> grantBadge(UUID playerId, String badgeId) {
        return CompletableFuture.runAsync(() -> {
            PlayerBadgeData data = playerDataCache.computeIfAbsent(playerId, k -> new PlayerBadgeData());
            data.addBadge(badgeId);
            savePlayerData(playerId, data);
        });
    }

    public static CompletableFuture<Void> revokeBadge(UUID playerId, String badgeId) {
        return CompletableFuture.runAsync(() -> {
            PlayerBadgeData data = playerDataCache.get(playerId);
            if (data != null) {
                data.removeBadge(badgeId);
                savePlayerData(playerId, data);
            }
        });
    }

    public static List<String> getPlayerBadges(UUID playerId) {
        PlayerBadgeData data = playerDataCache.get(playerId);
        return data != null ? new ArrayList<>(data.getOwnedBadges()) : new ArrayList<>();
    }

    public static List<String> getPlayerDisplayBadges(UUID playerId) {
        PlayerBadgeData data = playerDataCache.get(playerId);
        return data != null ? data.getDisplayBadges() : new ArrayList<>();
    }

    public static CompletableFuture<Void> setPlayerDisplayBadges(UUID playerId, List<String> badgeIds) {
        return CompletableFuture.runAsync(() -> {
            PlayerBadgeData data = playerDataCache.computeIfAbsent(playerId, k -> new PlayerBadgeData());
            data.setDisplayBadges(badgeIds);
            savePlayerData(playerId, data);
        });
    }

    public static String getPlayerDisplayName(UUID playerId) {
        PlayerBadgeData data = playerDataCache.get(playerId);
        return data != null ? data.getDisplayName() : "";
    }

    public static CompletableFuture<Void> setPlayerDisplayName(UUID playerId, String displayName) {
        return CompletableFuture.runAsync(() -> {
            PlayerBadgeData data = playerDataCache.computeIfAbsent(playerId, k -> new PlayerBadgeData());
            data.setDisplayName(displayName);
            savePlayerData(playerId, data);
        });
    }

    public static boolean canViewPlayerDisplay(UUID playerId) {
        PlayerBadgeData data = playerDataCache.get(playerId);
        return data == null || data.isAllowPublicView();
    }

    public static CompletableFuture<Void> setAllowPublicView(UUID playerId, boolean allow) {
        return CompletableFuture.runAsync(() -> {
            PlayerBadgeData data = playerDataCache.computeIfAbsent(playerId, k -> new PlayerBadgeData());
            data.setAllowPublicView(allow);
            savePlayerData(playerId, data);
        });
    }

    private static void savePlayerData(UUID playerId, PlayerBadgeData data) {
        switch (storageType) {
            case FILE:
                saveToFile();
                break;
            case SQLITE:
            case MYSQL:
                saveToDatabase(playerId, data);
                break;
        }
    }

    private static void saveToFile() {
        try {
            JsonObject rootObject = new JsonObject();
            for (Map.Entry<UUID, PlayerBadgeData> entry : playerDataCache.entrySet()) {
                rootObject.add(entry.getKey().toString(), entry.getValue().toJson());
            }

            Files.writeString(playerDataFile, GSON.toJson(rootObject));
        } catch (IOException e) {
            BadgeBox.LOGGER.error("Failed to save player data to file", e);
        }
    }

    private static void saveToDatabase(UUID playerId, PlayerBadgeData data) {
        try {
            dbConnection.setAutoCommit(false);

            // Update player data
            String updatePlayerData = """
                INSERT OR REPLACE INTO player_badge_data (player_uuid, display_name, allow_public_view)
                VALUES (?, ?, ?)
            """;
            try (PreparedStatement stmt = dbConnection.prepareStatement(updatePlayerData)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, data.getDisplayName());
                stmt.setBoolean(3, data.isAllowPublicView());
                stmt.executeUpdate();
            }

            // Update owned badges
            String deleteOwnedBadges = "DELETE FROM player_owned_badges WHERE player_uuid = ?";
            try (PreparedStatement stmt = dbConnection.prepareStatement(deleteOwnedBadges)) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }

            String insertOwnedBadge = "INSERT INTO player_owned_badges (player_uuid, badge_id) VALUES (?, ?)";
            try (PreparedStatement stmt = dbConnection.prepareStatement(insertOwnedBadge)) {
                for (String badgeId : data.getOwnedBadges()) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, badgeId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // Update display badges
            String deleteDisplayBadges = "DELETE FROM player_display_badges WHERE player_uuid = ?";
            try (PreparedStatement stmt = dbConnection.prepareStatement(deleteDisplayBadges)) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }

            String insertDisplayBadge = "INSERT INTO player_display_badges (player_uuid, badge_id, display_order) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = dbConnection.prepareStatement(insertDisplayBadge)) {
                List<String> displayBadges = data.getDisplayBadges();
                for (int i = 0; i < displayBadges.size(); i++) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, displayBadges.get(i));
                    stmt.setInt(3, i);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            dbConnection.commit();
        } catch (SQLException e) {
            try {
                dbConnection.rollback();
            } catch (SQLException rollbackEx) {
                BadgeBox.LOGGER.error("Failed to rollback transaction", rollbackEx);
            }
            BadgeBox.LOGGER.error("Failed to save player data to database", e);
        } finally {
            try {
                dbConnection.setAutoCommit(true);
            } catch (SQLException e) {
                BadgeBox.LOGGER.error("Failed to reset auto-commit", e);
            }
        }
    }

    public static void shutdown() {
        if (storageType == StorageType.FILE) {
            saveToFile();
        }

        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException e) {
                BadgeBox.LOGGER.error("Failed to close database connection", e);
            }
        }
    }

    // Bulk operations for admin usage
    public static CompletableFuture<Void> grantBadgeToMultiplePlayers(List<UUID> playerIds, String badgeId) {
        return CompletableFuture.runAsync(() -> {
            for (UUID playerId : playerIds) {
                PlayerBadgeData data = playerDataCache.computeIfAbsent(playerId, k -> new PlayerBadgeData());
                data.addBadge(badgeId);
                savePlayerData(playerId, data);
            }
        });
    }

    public static Map<UUID, Integer> getBadgeStatistics() {
        Map<UUID, Integer> stats = new HashMap<>();
        for (Map.Entry<UUID, PlayerBadgeData> entry : playerDataCache.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().getOwnedBadges().size());
        }
        return stats;
    }
}
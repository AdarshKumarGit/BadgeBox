package org.chubby.github.badgebox.client;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.BadgeDataManager;
import org.chubby.github.badgebox.BadgeNetworking;
import org.chubby.github.badgebox.PlayerBadgeData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class BadgeClientNetworking {

    // Client-side cache for player badge data
    private static final Map<UUID, PlayerBadgeData> clientBadgeCache = new ConcurrentHashMap<>();

    public static void registerClientHandlers() {
        // Handle badge data sync from server
        ClientPlayNetworking.registerGlobalReceiver(
                BadgeNetworking.SyncPlayerBadgeDataPayload.ID,
                (payload, context) -> {
                    // Update client cache
                    clientBadgeCache.put(payload.playerId(), payload.data());

                    // Update the main data manager if it's the current player
                    MinecraftClient client = context.client();
                    if (client.player != null && client.player.getUuid().equals(payload.playerId())) {
                        context.client().execute(() -> {
                            // Update local data for the current player
                            // This ensures consistency between client and server
                        });
                    }
                }
        );
    }

    // Methods to send packets to server
    public static void openBadgeEditor() {
        ClientPlayNetworking.send(new BadgeNetworking.OpenBadgeEditorPayload());
    }

    public static void openBadgeDisplay(UUID targetPlayer) {
        ClientPlayNetworking.send(new BadgeNetworking.OpenBadgeDisplayPayload(targetPlayer));
    }

    public static void updateBadgeDisplay(int slot, Identifier badgeId) {
        ClientPlayNetworking.send(new BadgeNetworking.UpdateBadgeDisplayPayload(slot, badgeId));
    }

    public static void requestBadgeDataSync(UUID targetPlayer) {
        ClientPlayNetworking.send(new BadgeNetworking.RequestBadgeDataSyncPayload(targetPlayer));
    }

    // Get cached badge data
    public static PlayerBadgeData getCachedBadgeData(UUID playerId) {
        return clientBadgeCache.get(playerId);
    }

    // Clear cache (useful for logout/disconnect)
    public static void clearCache() {
        clientBadgeCache.clear();
    }

    // Check if we have cached data for a player
    public static boolean hasCachedData(UUID playerId) {
        return clientBadgeCache.containsKey(playerId);
    }
}
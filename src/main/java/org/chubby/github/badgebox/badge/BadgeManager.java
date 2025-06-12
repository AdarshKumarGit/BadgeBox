package org.chubby.github.badgebox.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;
import org.chubby.github.badgebox.storage.BadgeStorage;

import java.util.*;
import java.util.stream.Collectors;

public class BadgeManager {
    private static final Map<String, Badge> badges = new HashMap<>();
    private static final Map<String, List<String>> categorizedBadges = new HashMap<>();

    public static void registerBadge(Badge badge) {
        badges.put(badge.getId(), badge);
        categorizedBadges.computeIfAbsent(badge.getCategory(), k -> new ArrayList<>()).add(badge.getId());
    }

    public static Badge getBadge(String id) {
        return badges.get(id);
    }

    public static Collection<Badge> getAllBadges() {
        return badges.values();
    }

    public static List<Badge> getBadgesByCategory(String category) {
        return categorizedBadges.getOrDefault(category, new ArrayList<>())
                .stream()
                .map(badges::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static Set<String> getCategories() {
        return categorizedBadges.keySet();
    }

    public static void grantBadge(ServerPlayerEntity player, String badgeId) {
        Badge badge = getBadge(badgeId);
        if (badge != null) {
            BadgeStorage.grantBadge(player.getUuid(), badgeId);
        }
    }

    public static void revokeBadge(ServerPlayerEntity player, String badgeId) {
        BadgeStorage.revokeBadge(player.getUuid(), badgeId);
    }

    public static List<String> getPlayerBadges(UUID playerId) {
        return BadgeStorage.getPlayerBadges(playerId);
    }

    public static List<String> getPlayerDisplayBadges(UUID playerId) {
        return BadgeStorage.getPlayerDisplayBadges(playerId);
    }

    public static void setPlayerDisplayBadges(UUID playerId, List<String> badgeIds) {
        BadgeStorage.setPlayerDisplayBadges(playerId, badgeIds);
    }
}

package org.chubby.github.badgebox;

import net.minecraft.util.Identifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeRegistry {
    private static final Map<Identifier, Badge> badges = new ConcurrentHashMap<>();
    private static final Map<String, List<Badge>> categories = new ConcurrentHashMap<>();

    public static void init() {
        // Initialize default categories
        categories.put("gym", new ArrayList<>());
        categories.put("elite_four", new ArrayList<>());
        categories.put("champion", new ArrayList<>());
        categories.put("special", new ArrayList<>());
        categories.put("event", new ArrayList<>());
    }

    public static void registerBadge(Badge badge) {
        badges.put(badge.getId(), badge);
        categories.computeIfAbsent(badge.getCategory(), k -> new ArrayList<>()).add(badge);
    }

    public static Badge getBadge(Identifier id) {
        return badges.get(id);
    }

    public static Collection<Badge> getAllBadges() {
        return badges.values();
    }

    public static List<Badge> getBadgesByCategory(String category) {
        return categories.getOrDefault(category, new ArrayList<>());
    }

    public static Set<String> getCategories() {
        return categories.keySet();
    }

    public static boolean badgeExists(Identifier id) {
        return badges.containsKey(id);
    }

    public static void clearBadges() {
        badges.clear();
        categories.clear();
        init();
    }
}
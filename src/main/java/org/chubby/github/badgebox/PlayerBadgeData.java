package org.chubby.github.badgebox;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import java.util.*;

public class PlayerBadgeData {
    private final UUID playerId;
    private final Set<Identifier> ownedBadges;
    private final List<Identifier> displayBadges; // 8 badges for display case
    private String displayName;

    public PlayerBadgeData(UUID playerId) {
        this.playerId = playerId;
        this.ownedBadges = new HashSet<>();
        this.displayBadges = new ArrayList<>(Collections.nCopies(8, null));
        this.displayName = "";
    }

    public UUID getPlayerId() { return playerId; }
    public Set<Identifier> getOwnedBadges() { return new HashSet<>(ownedBadges); }
    public List<Identifier> getDisplayBadges() { return new ArrayList<>(displayBadges); }
    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean hasBadge(Identifier badgeId) {
        return ownedBadges.contains(badgeId);
    }

    public void addBadge(Identifier badgeId) {
        ownedBadges.add(badgeId);
    }

    public void removeBadge(Identifier badgeId) {
        ownedBadges.remove(badgeId);
        // Remove from display if present
        for (int i = 0; i < displayBadges.size(); i++) {
            if (Objects.equals(displayBadges.get(i), badgeId)) {
                displayBadges.set(i, null);
            }
        }
    }

    public boolean setDisplayBadge(int slot, Identifier badgeId) {
        if (slot < 0 || slot >= 8) return false;
        if (badgeId != null && !ownedBadges.contains(badgeId)) return false;

        displayBadges.set(slot, badgeId);
        return true;
    }

    public Identifier getDisplayBadge(int slot) {
        if (slot < 0 || slot >= 8) return null;
        return displayBadges.get(slot);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("playerId", playerId);
        nbt.putString("displayName", displayName);

        // Save owned badges
        NbtCompound ownedNbt = new NbtCompound();
        int i = 0;
        for (Identifier badge : ownedBadges) {
            ownedNbt.putString(String.valueOf(i++), badge.toString());
        }
        nbt.put("ownedBadges", ownedNbt);

        // Save display badges
        NbtCompound displayNbt = new NbtCompound();
        for (int j = 0; j < displayBadges.size(); j++) {
            if (displayBadges.get(j) != null) {
                displayNbt.putString(String.valueOf(j), displayBadges.get(j).toString());
            }
        }
        nbt.put("displayBadges", displayNbt);

        return nbt;
    }

    public static PlayerBadgeData fromNbt(NbtCompound nbt) {
        UUID playerId = nbt.getUuid("playerId");
        PlayerBadgeData data = new PlayerBadgeData(playerId);
        data.displayName = nbt.getString("displayName");

        // Load owned badges
        NbtCompound ownedNbt = nbt.getCompound("ownedBadges");
        int i = 0;
        while (ownedNbt.contains(String.valueOf(i))) {
            data.ownedBadges.add(Identifier.of(ownedNbt.getString(String.valueOf(i))));
            i++;
        }

        // Load display badges
        NbtCompound displayNbt = nbt.getCompound("displayBadges");
        for (int j = 0; j < 8; j++) {
            if (displayNbt.contains(String.valueOf(j))) {
                data.displayBadges.set(j, Identifier.of(displayNbt.getString(String.valueOf(j))));
            }
        }

        return data;
    }
}
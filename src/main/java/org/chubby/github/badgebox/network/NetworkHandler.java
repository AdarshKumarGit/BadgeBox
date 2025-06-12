package org.chubby.github.badgebox.network;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.BadgeBox;
import org.chubby.github.badgebox.badge.Badge;
import org.chubby.github.badgebox.badge.BadgeManager;
import org.chubby.github.badgebox.gui.BadgeEditorScreenHandler;
import org.chubby.github.badgebox.storage.BadgeStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class NetworkHandler {
    static Codec<UUID> UUID = Codec.STRING.xmap(java.util.UUID::fromString, java.util.UUID::toString);
    // Packet IDs
    public static final Identifier OPEN_BADGE_GUI = BadgeBox.id("open_badge_gui");
    public static final Identifier REQUEST_PLAYER_BADGES = BadgeBox.id("request_player_badges");
    public static final Identifier SYNC_PLAYER_BADGES = BadgeBox.id("sync_player_badges");
    public static final Identifier UPDATE_DISPLAY_BADGES = BadgeBox.id("update_display_badges");
    public static final Identifier REQUEST_BADGE_CATEGORIES = BadgeBox.id("request_badge_categories");
    public static final Identifier SYNC_BADGE_CATEGORIES = BadgeBox.id("sync_badge_categories");
    public static final Identifier UPDATE_DISPLAY_NAME = BadgeBox.id("update_display_name");
    public static final Identifier TOGGLE_PUBLIC_VIEW = BadgeBox.id("toggle_public_view");
    public static final Identifier VIEW_PLAYER_BADGES = BadgeBox.id("view_player_badges");

    // Payload records for each packet type
    public record OpenBadgeGuiPayload() implements CustomPayload {
        public static final CustomPayload.Id<OpenBadgeGuiPayload> ID = new CustomPayload.Id<>(OPEN_BADGE_GUI);
        public static final PacketCodec<RegistryByteBuf, OpenBadgeGuiPayload> CODEC =
                PacketCodec.unit(new OpenBadgeGuiPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestPlayerBadgesPayload(UUID playerId) implements CustomPayload {
        public static final CustomPayload.Id<RequestPlayerBadgesPayload> ID = new CustomPayload.Id<>(REQUEST_PLAYER_BADGES);
        public static final PacketCodec<RegistryByteBuf, RequestPlayerBadgesPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.codec(UUID), RequestPlayerBadgesPayload::playerId,
                        RequestPlayerBadgesPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SyncPlayerBadgesPayload(UUID playerId, List<String> ownedBadges, List<String> displayBadges,
                                          String displayName, boolean allowPublicView) implements CustomPayload {
        public static final CustomPayload.Id<SyncPlayerBadgesPayload> ID = new CustomPayload.Id<>(SYNC_PLAYER_BADGES);
        public static final PacketCodec<RegistryByteBuf, SyncPlayerBadgesPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.codec(UUID), SyncPlayerBadgesPayload::playerId,
                        PacketCodecs.STRING.collect(PacketCodecs.toList()), SyncPlayerBadgesPayload::ownedBadges,
                        PacketCodecs.STRING.collect(PacketCodecs.toList()), SyncPlayerBadgesPayload::displayBadges,
                        PacketCodecs.STRING, SyncPlayerBadgesPayload::displayName,
                        PacketCodecs.BOOL, SyncPlayerBadgesPayload::allowPublicView,
                        SyncPlayerBadgesPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdateDisplayBadgesPayload(List<String> badgeIds) implements CustomPayload {
        public static final CustomPayload.Id<UpdateDisplayBadgesPayload> ID = new CustomPayload.Id<>(UPDATE_DISPLAY_BADGES);
        public static final PacketCodec<RegistryByteBuf, UpdateDisplayBadgesPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING.collect(PacketCodecs.toList()), UpdateDisplayBadgesPayload::badgeIds,
                        UpdateDisplayBadgesPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestBadgeCategoriesPayload() implements CustomPayload {
        public static final CustomPayload.Id<RequestBadgeCategoriesPayload> ID = new CustomPayload.Id<>(REQUEST_BADGE_CATEGORIES);
        public static final PacketCodec<RegistryByteBuf, RequestBadgeCategoriesPayload> CODEC =
                PacketCodec.unit(new RequestBadgeCategoriesPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SyncBadgeCategoriesPayload(List<String> categories, List<BadgeData> badges) implements CustomPayload {
        public static final CustomPayload.Id<SyncBadgeCategoriesPayload> ID = new CustomPayload.Id<>(SYNC_BADGE_CATEGORIES);
        public static final PacketCodec<RegistryByteBuf, SyncBadgeCategoriesPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING.collect(PacketCodecs.toList()), SyncBadgeCategoriesPayload::categories,
                        BadgeData.CODEC.collect(PacketCodecs.toList()), SyncBadgeCategoriesPayload::badges,
                        SyncBadgeCategoriesPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdateDisplayNamePayload(String displayName) implements CustomPayload {
        public static final CustomPayload.Id<UpdateDisplayNamePayload> ID = new CustomPayload.Id<>(UPDATE_DISPLAY_NAME);
        public static final PacketCodec<RegistryByteBuf, UpdateDisplayNamePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, UpdateDisplayNamePayload::displayName,
                        UpdateDisplayNamePayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record TogglePublicViewPayload(boolean allowPublicView) implements CustomPayload {
        public static final CustomPayload.Id<TogglePublicViewPayload> ID = new CustomPayload.Id<>(TOGGLE_PUBLIC_VIEW);
        public static final PacketCodec<RegistryByteBuf, TogglePublicViewPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.BOOL, TogglePublicViewPayload::allowPublicView,
                        TogglePublicViewPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ViewPlayerBadgesPayload(UUID targetPlayerId) implements CustomPayload {
        public static final CustomPayload.Id<ViewPlayerBadgesPayload> ID = new CustomPayload.Id<>(VIEW_PLAYER_BADGES);
        public static final PacketCodec<RegistryByteBuf, ViewPlayerBadgesPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.codec(UUID), ViewPlayerBadgesPayload::targetPlayerId,
                        ViewPlayerBadgesPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Badge data record for network transmission
    public record BadgeData(String id, String name, String category, String description,
                            String texture, boolean obtainable) {
        public static final PacketCodec<RegistryByteBuf, BadgeData> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, BadgeData::id,
                        PacketCodecs.STRING, BadgeData::name,
                        PacketCodecs.STRING, BadgeData::category,
                        PacketCodecs.STRING, BadgeData::description,
                        PacketCodecs.STRING, BadgeData::texture,
                        PacketCodecs.BOOL, BadgeData::obtainable,
                        BadgeData::new
                );

        public static BadgeData fromBadge(Badge badge) {
            return new BadgeData(
                    badge.getId(),
                    badge.getName(),
                    badge.getCategory(),
                    badge.getDescription(),
                    badge.getTexture(),
                    badge.isObtainable()
            );
        }
    }

    public static void init() {
        // Register payload types
        PayloadTypeRegistry.playS2C().register(SyncPlayerBadgesPayload.ID, SyncPlayerBadgesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBadgeCategoriesPayload.ID, SyncBadgeCategoriesPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(OpenBadgeGuiPayload.ID, OpenBadgeGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPlayerBadgesPayload.ID, RequestPlayerBadgesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDisplayBadgesPayload.ID, UpdateDisplayBadgesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBadgeCategoriesPayload.ID, RequestBadgeCategoriesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDisplayNamePayload.ID, UpdateDisplayNamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TogglePublicViewPayload.ID, TogglePublicViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ViewPlayerBadgesPayload.ID, ViewPlayerBadgesPayload.CODEC);

        // Register server-side packet handlers
        ServerPlayNetworking.registerGlobalReceiver(OpenBadgeGuiPayload.ID, NetworkHandler::handleOpenBadgeGui);
        ServerPlayNetworking.registerGlobalReceiver(RequestPlayerBadgesPayload.ID, NetworkHandler::handleRequestPlayerBadges);
        ServerPlayNetworking.registerGlobalReceiver(UpdateDisplayBadgesPayload.ID, NetworkHandler::handleUpdateDisplayBadges);
        ServerPlayNetworking.registerGlobalReceiver(RequestBadgeCategoriesPayload.ID, NetworkHandler::handleRequestBadgeCategories);
        ServerPlayNetworking.registerGlobalReceiver(UpdateDisplayNamePayload.ID, NetworkHandler::handleUpdateDisplayName);
        ServerPlayNetworking.registerGlobalReceiver(TogglePublicViewPayload.ID, NetworkHandler::handleTogglePublicView);
        ServerPlayNetworking.registerGlobalReceiver(ViewPlayerBadgesPayload.ID, NetworkHandler::handleViewPlayerBadges);
    }

    // Server-side packet handlers
    private static void handleOpenBadgeGui(OpenBadgeGuiPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Badge Editor");
            }

            @Override
            public @NotNull ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
                return new BadgeEditorScreenHandler(syncId, playerInventory);
            }
        });
    }

    private static void handleRequestPlayerBadges(RequestPlayerBadgesPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        UUID targetPlayerId = payload.playerId();

        // Check if the requesting player can view the target player's badges
        if (!targetPlayerId.equals(player.getUuid()) && !BadgeStorage.canViewPlayerDisplay(targetPlayerId)) {
            player.sendMessage(Text.literal("This player's badges are private."), false);
            return;
        }

        syncPlayerBadges(player, targetPlayerId);
    }

    private static void handleUpdateDisplayBadges(UpdateDisplayBadgesPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        List<String> badgeIds = payload.badgeIds();

        // Validate that the player owns all the badges they're trying to display
        List<String> ownedBadges = BadgeStorage.getPlayerBadges(player.getUuid());
        for (String badgeId : badgeIds) {
            if (!ownedBadges.contains(badgeId)) {
                player.sendMessage(Text.literal("You cannot display a badge you don't own!"), false);
                return;
            }
        }

        // Limit to 8 display badges
        if (badgeIds.size() > 8) {
            badgeIds = badgeIds.subList(0, 8);
        }

        BadgeStorage.setPlayerDisplayBadges(player.getUuid(), badgeIds);
        player.sendMessage(Text.literal("Display badges updated successfully!"), false);

        // Sync the updated data back to the client
        syncPlayerBadges(player, player.getUuid());
    }

    private static void handleRequestBadgeCategories(RequestBadgeCategoriesPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();

        List<String> categories = BadgeManager.getCategories().stream().toList();
        List<BadgeData> badgeData = BadgeManager.getAllBadges().stream()
                .map(BadgeData::fromBadge)
                .toList();

        ServerPlayNetworking.send(player, new SyncBadgeCategoriesPayload(categories, badgeData));
    }

    private static void handleUpdateDisplayName(UpdateDisplayNamePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        String displayName = payload.displayName();

        // Validate display name (basic validation)
        if (displayName.length() > 32) {
            player.sendMessage(Text.literal("Display name is too long! Maximum 32 characters."), false);
            return;
        }

        BadgeStorage.setPlayerDisplayName(player.getUuid(), displayName);
        player.sendMessage(Text.literal("Display name updated to: " + displayName), false);

        // Sync the updated data back to the client
        syncPlayerBadges(player, player.getUuid());
    }

    private static void handleTogglePublicView(TogglePublicViewPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        boolean allowPublicView = payload.allowPublicView();

        BadgeStorage.setAllowPublicView(player.getUuid(), allowPublicView);
        String message = allowPublicView ? "Your badges are now public!" : "Your badges are now private!";
        player.sendMessage(Text.literal(message), false);

        // Sync the updated data back to the client
        syncPlayerBadges(player, player.getUuid());
    }

    private static void handleViewPlayerBadges(ViewPlayerBadgesPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        UUID targetPlayerId = payload.targetPlayerId();

        // Check if the target player allows public viewing
        if (!BadgeStorage.canViewPlayerDisplay(targetPlayerId)) {
            player.sendMessage(Text.literal("This player's badges are private."), false);
            return;
        }

        syncPlayerBadges(player, targetPlayerId);
    }

    // Utility methods
    public static void syncPlayerBadges(ServerPlayerEntity player, UUID targetPlayerId) {
        List<String> ownedBadges = BadgeStorage.getPlayerBadges(targetPlayerId);
        List<String> displayBadges = BadgeStorage.getPlayerDisplayBadges(targetPlayerId);
        String displayName = BadgeStorage.getPlayerDisplayName(targetPlayerId);
        boolean allowPublicView = BadgeStorage.canViewPlayerDisplay(targetPlayerId);

        ServerPlayNetworking.send(player, new SyncPlayerBadgesPayload(
                targetPlayerId, ownedBadges, displayBadges, displayName, allowPublicView
        ));
    }

    public static void syncAllPlayerBadges(ServerPlayerEntity player) {
        syncPlayerBadges(player, player.getUuid());
    }

    public static void notifyBadgeGranted(ServerPlayerEntity player, String badgeId) {
        Badge badge = BadgeManager.getBadge(badgeId);
        if (badge != null) {
            player.sendMessage(Text.literal("§6✦ §eBadge Unlocked: §f" + badge.getName() + " §7- " + badge.getDescription()), false);
            // Optionally play a sound or show a title
            syncPlayerBadges(player, player.getUuid());
        }
    }

    public static void notifyBadgeRevoked(ServerPlayerEntity player, String badgeId) {
        Badge badge = BadgeManager.getBadge(badgeId);
        if (badge != null) {
            player.sendMessage(Text.literal("§c✦ §eBadge Removed: §f" + badge.getName()), false);
            syncPlayerBadges(player, player.getUuid());
        }
    }
}
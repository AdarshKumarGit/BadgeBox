package org.chubby.github.badgebox;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.chubby.github.badgebox.Badgebox;
import org.chubby.github.badgebox.BadgeDataManager;
import org.chubby.github.badgebox.BadgeDisplayScreenHandler;
import org.chubby.github.badgebox.BadgeEditorScreenHandler;
import org.chubby.github.badgebox.PlayerBadgeData;

import java.util.UUID;

public class BadgeNetworking {

    // Payload for opening badge editor
    public record OpenBadgeEditorPayload() implements CustomPayload {
        public static final CustomPayload.Id<OpenBadgeEditorPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Badgebox.MOD_ID, "open_badge_editor"));

        public static final PacketCodec<RegistryByteBuf, OpenBadgeEditorPayload> CODEC =
                PacketCodec.unit(new OpenBadgeEditorPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Payload for opening badge display
    public record OpenBadgeDisplayPayload(UUID targetPlayer) implements CustomPayload {
        public static final CustomPayload.Id<OpenBadgeDisplayPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Badgebox.MOD_ID, "open_badge_display"));

        public static final PacketCodec<RegistryByteBuf, OpenBadgeDisplayPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeUuid(value.targetPlayer),
                        buf -> new OpenBadgeDisplayPayload(buf.readUuid())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Payload for updating badge display
    public record UpdateBadgeDisplayPayload(int slot, Identifier badgeId) implements CustomPayload {
        public static final CustomPayload.Id<UpdateBadgeDisplayPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Badgebox.MOD_ID, "update_badge_display"));

        public static final PacketCodec<RegistryByteBuf, UpdateBadgeDisplayPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeInt(value.slot);
                            buf.writeBoolean(value.badgeId != null);
                            if (value.badgeId != null) {
                                buf.writeIdentifier(value.badgeId);
                            }
                        },
                        buf -> {
                            int slot = buf.readInt();
                            boolean hasBadge = buf.readBoolean();
                            Identifier badgeId = hasBadge ? buf.readIdentifier() : null;
                            return new UpdateBadgeDisplayPayload(slot, badgeId);
                        }
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Payload for syncing player badge data to client
    public record SyncPlayerBadgeDataPayload(UUID playerId, PlayerBadgeData data) implements CustomPayload {
        public static final CustomPayload.Id<SyncPlayerBadgeDataPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Badgebox.MOD_ID, "sync_player_badge_data"));

        public static final PacketCodec<RegistryByteBuf, SyncPlayerBadgeDataPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeUuid(value.playerId);
                            buf.writeNbt(value.data.toNbt());
                        },
                        buf -> {
                            UUID playerId = buf.readUuid();
                            PlayerBadgeData data = PlayerBadgeData.fromNbt(buf.readNbt());
                            return new SyncPlayerBadgeDataPayload(playerId, data);
                        }
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Payload for requesting badge data sync
    public record RequestBadgeDataSyncPayload(UUID targetPlayer) implements CustomPayload {
        public static final CustomPayload.Id<RequestBadgeDataSyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Badgebox.MOD_ID, "request_badge_data_sync"));

        public static final PacketCodec<RegistryByteBuf, RequestBadgeDataSyncPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeUuid(value.targetPlayer),
                        buf -> new RequestBadgeDataSyncPayload(buf.readUuid())
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void registerPayloads() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(OpenBadgeEditorPayload.ID, OpenBadgeEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenBadgeDisplayPayload.ID, OpenBadgeDisplayPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateBadgeDisplayPayload.ID, UpdateBadgeDisplayPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBadgeDataSyncPayload.ID, RequestBadgeDataSyncPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncPlayerBadgeDataPayload.ID, SyncPlayerBadgeDataPayload.CODEC);
    }

    public static void registerServerHandlers() {
        // Handle opening badge editor
        ServerPlayNetworking.registerGlobalReceiver(OpenBadgeEditorPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            context.server().execute(() -> {
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        BadgeEditorScreenHandler::new,
                        Text.literal("Badge Editor - " + player.getName().getString())
                ));
            });
        });

        // Handle opening badge display
        ServerPlayNetworking.registerGlobalReceiver(OpenBadgeDisplayPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID targetPlayerId = payload.targetPlayer();

            context.server().execute(() -> {
                PlayerBadgeData displayData = BadgeDataManager.getPlayerData(targetPlayerId);

                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inventory, p) -> new BadgeDisplayScreenHandler(syncId, inventory, p, displayData),
                        Text.literal("Badge Display - " +
                                (displayData.getDisplayName().isEmpty() ? "Player" : displayData.getDisplayName()))
                ));
            });
        });

        // Handle updating badge display
        ServerPlayNetworking.registerGlobalReceiver(UpdateBadgeDisplayPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            context.server().execute(() -> {
                PlayerBadgeData playerData = BadgeDataManager.getPlayerData(player.getUuid());
                playerData.setDisplayBadge(payload.slot(), payload.badgeId());

                // Save the updated data
                BadgeDataManager.savePlayerData();
            });
        });

        // Handle badge data sync requests
        ServerPlayNetworking.registerGlobalReceiver(RequestBadgeDataSyncPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID targetPlayerId = payload.targetPlayer();

            context.server().execute(() -> {
                PlayerBadgeData targetData = BadgeDataManager.getPlayerData(targetPlayerId);

                // Send the data back to the requesting player
                ServerPlayNetworking.send(player, new SyncPlayerBadgeDataPayload(targetPlayerId, targetData));
            });
        });
    }

    // Utility methods for sending packets
    public static void sendBadgeDataSync(ServerPlayerEntity player, UUID targetPlayerId) {
        PlayerBadgeData data = BadgeDataManager.getPlayerData(targetPlayerId);
        ServerPlayNetworking.send(player, new SyncPlayerBadgeDataPayload(targetPlayerId, data));
    }

    public static void broadcastBadgeDataUpdate(ServerPlayerEntity updatedPlayer) {
        PlayerBadgeData data = BadgeDataManager.getPlayerData(updatedPlayer.getUuid());
        SyncPlayerBadgeDataPayload payload = new SyncPlayerBadgeDataPayload(updatedPlayer.getUuid(), data);

        // Send to all players who might be viewing this player's badges
        for (ServerPlayerEntity player : updatedPlayer.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
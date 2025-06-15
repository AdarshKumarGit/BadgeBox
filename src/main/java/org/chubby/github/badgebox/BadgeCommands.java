package org.chubby.github.badgebox;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BadgeCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(BadgeCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {

        dispatcher.register(CommandManager.literal("badge")
                .then(CommandManager.literal("give")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("badge", IdentifierArgumentType.identifier())
                                        .executes(BadgeCommands::giveBadge))))

                .then(CommandManager.literal("remove")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("badge", IdentifierArgumentType.identifier())
                                        .executes(BadgeCommands::removeBadge))))

                .then(CommandManager.literal("list")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BadgeCommands::listPlayerBadges)))

                .then(CommandManager.literal("editor")
                        .executes(BadgeCommands::openBadgeEditor))

                .then(CommandManager.literal("display")
                        .executes(BadgeCommands::openBadgeDisplay))

                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(BadgeCommands::reloadBadges))
        );
    }

    private static int giveBadge(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            Identifier badgeId = IdentifierArgumentType.getIdentifier(context, "badge");

            if (!BadgeRegistry.badgeExists(badgeId)) {
                context.getSource().sendError(Text.literal("Badge not found: " + badgeId));
                return 0;
            }

            BadgeDataManager.getPlayerData(player.getUuid()).addBadge(badgeId);

            context.getSource().sendFeedback(() ->
                    Text.literal("Gave badge " + badgeId + " to " + player.getName().getString()), true);

            player.sendMessage(Text.literal("§aYou received a new badge: " +
                    BadgeRegistry.getBadge(badgeId).getName()));

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error giving badge: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeBadge(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            Identifier badgeId = IdentifierArgumentType.getIdentifier(context, "badge");

            BadgeDataManager.getPlayerData(player.getUuid()).removeBadge(badgeId);

            context.getSource().sendFeedback(() ->
                    Text.literal("Removed badge " + badgeId + " from " + player.getName().getString()), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error removing badge: " + e.getMessage()));
            return 0;
        }
    }

    private static int listPlayerBadges(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            var playerData = BadgeDataManager.getPlayerData(player.getUuid());
            var ownedBadges = playerData.getOwnedBadges();

            if (ownedBadges.isEmpty()) {
                context.getSource().sendFeedback(() ->
                        Text.literal(player.getName().getString() + " has no badges"), false);
            } else {
                context.getSource().sendFeedback(() ->
                        Text.literal(player.getName().getString() + " has " + ownedBadges.size() + " badges:"), false);

                for (Identifier badgeId : ownedBadges) {
                    var badge = BadgeRegistry.getBadge(badgeId);
                    if (badge != null) {
                        context.getSource().sendFeedback(() ->
                                Text.literal("- " + badge.getName() + " (" + badgeId + ")"), false);
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error listing badges: " + e.getMessage()));
            return 0;
        }
    }

    private static int openBadgeEditor(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, p) -> new BadgeEditorScreenHandler(syncId, inventory, p),
                    Text.literal("Badge Editor - " + player.getName().getString())
            ));

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error opening badge editor: " + e.getMessage()));
            return 0;
        }
    }

    private static int openBadgeDisplay(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

            // Open badge display case
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, p) -> new BadgeDisplayScreenHandler(syncId, inventory, p),
                    Text.literal("Badge Display - " + player.getName().getString())
            ));

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error opening badge display: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadBadges(CommandContext<ServerCommandSource> context) {
        try {
            BadgeDataManager.loadBadges();
            context.getSource().sendFeedback(() -> Text.literal("Badges reloaded successfully"), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error reloading badges: " + e.getMessage()));
            return 0;
        }
    }
}
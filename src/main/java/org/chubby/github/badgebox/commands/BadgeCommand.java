package org.chubby.github.badgebox.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.chubby.github.badgebox.badge.Badge;
import org.chubby.github.badgebox.badge.BadgeManager;

import java.util.List;

public class BadgeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("badge")
                .requires(source -> source.hasPermissionLevel(2)) // Op level 2
                .then(CommandManager.literal("give")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("badge", StringArgumentType.string())
                                        .executes(BadgeCommand::giveBadge))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("badge", StringArgumentType.string())
                                        .executes(BadgeCommand::removeBadge))))
                .then(CommandManager.literal("list")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BadgeCommand::listPlayerBadges)))
                .then(CommandManager.literal("reload")
                        .executes(BadgeCommand::reloadConfig)));
    }

    private static int giveBadge(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            String badgeId = StringArgumentType.getString(context, "badge");

            Badge badge = BadgeManager.getBadge(badgeId);
            if (badge == null) {
                context.getSource().sendError(Text.literal("Badge '" + badgeId + "' does not exist!"));
                return 0;
            }

            BadgeManager.grantBadge(player, badgeId);
            context.getSource().sendFeedback(() -> Text.literal("Granted badge '" + badge.getName() + "' to " + player.getName().getString()), true);
            player.sendMessage(Text.literal("You have been awarded the badge: " + badge.getName()));

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeBadge(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            String badgeId = StringArgumentType.getString(context, "badge");

            Badge badge = BadgeManager.getBadge(badgeId);
            if (badge == null) {
                context.getSource().sendError(Text.literal("Badge '" + badgeId + "' does not exist!"));
                return 0;
            }

            BadgeManager.revokeBadge(player, badgeId);
            context.getSource().sendFeedback(() -> Text.literal("Removed badge '" + badge.getName() + "' from " + player.getName().getString()), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int listPlayerBadges(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            List<String> badges = BadgeManager.getPlayerBadges(player.getUuid());

            if (badges.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal(player.getName().getString() + " has no badges"), false);
            } else {
                context.getSource().sendFeedback(() -> Text.literal(player.getName().getString() + " has " + badges.size() + " badges:"), false);
                for (String badgeId : badges) {
                    Badge badge = BadgeManager.getBadge(badgeId);
                    if (badge != null) {
                        context.getSource().sendFeedback(() -> Text.literal("- " + badge.getName() + " (" + badgeId + ")"), false);
                    }
                }
            }

            return badges.size();
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        // TODO: Implement config reloading
        context.getSource().sendFeedback(() -> Text.literal("Config reload not yet implemented"), false);
        return 1;
    }
}
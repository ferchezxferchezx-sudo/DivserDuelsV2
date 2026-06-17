package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DuelCommand implements CommandExecutor {
    private final DuelManager duelManager;
    private final ConfigManager configManager;

    public DuelCommand(DuelManager duelManager, ConfigManager configManager) {
        this.duelManager = duelManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getErrorPlayerOnly());
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(configManager.format("§6Usage: /duel <player>§7, §6/duel accept <id>§7, §6/duel deny <id>§7, §6/duel leave§7, §6/duel leaderboard"));
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length != 2) {
                player.sendMessage(configManager.format("§6Usage: /duel accept <id>"));
                return true;
            }
            try {
                UUID requestId = UUID.fromString(args[1]);
                duelManager.acceptRequest(player, requestId);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(configManager.getErrorInvalidRequestId());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            if (args.length != 2) {
                player.sendMessage(configManager.format("§6Usage: /duel deny <id>"));
                return true;
            }
            try {
                UUID requestId = UUID.fromString(args[1]);
                duelManager.denyRequest(player, requestId);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(configManager.getErrorInvalidRequestId());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave") && args.length == 1) {
            DuelSession session = duelManager.getSession(player);
            if (session == null) {
                player.sendMessage(configManager.format("§cYou are not currently in a duel."));
                return true;
            }
            if (!session.isKeepInventorySession()) {
                player.sendMessage(configManager.format("§cYou can only leave if keep inventory was enabled for this duel."));
                return true;
            }
            Player winner = session.getOpponent(player);
            session.finishDuel(winner, player, "Player left the duel");
            return true;
        }
        if (args[0].equalsIgnoreCase("leaderboard") && args.length == 1) {
            String display = duelManager.getLeaderboardDisplay();
            for (String line : display.split("\\n")) {
                player.sendMessage(line);
            }
            return true;
        }
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(configManager.getErrorPlayerNotFound());
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(configManager.getErrorCantDuelSelf());
                return true;
            }
            duelManager.openConfigurationMenu(player, target);
            return true;
        }
        player.sendMessage(configManager.format("§6Usage: /duel <player>§7, §6/duel accept <id>§7, §6/duel deny <id>§7, §6/duel leave"));
        return true;
    }
}

package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCommand implements CommandExecutor {
    private final PartyManager partyManager;
    private final ConfigManager configManager;

    public PartyCommand(PartyManager partyManager, ConfigManager configManager) {
        this.partyManager = partyManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getErrorPlayerOnly());
            return true;
        }
        try {
        if (args.length == 0) {
            // Open main party menu (teams and 1v1 options)
            try {
                partyManager.openPartyMenu(player);
            } catch (Exception ex) {
                pluginLog("Failed to open party menu: " + ex.getMessage());
                player.sendMessage(configManager.format("§cUnable to open party menu."));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            player.sendMessage(configManager.format("§6Party Commands: §e/party create§7, §e/party invite <player>§7, §e/party leave§7, §e/party join <leader>§7, §e/party disband§7, §e/party kick <player>§7, §e/party settings§7, §e/party teams§7, §e/party start"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> partyManager.createParty(player);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(configManager.format("§6Usage: /party invite <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(configManager.getErrorPlayerNotFound());
                    return true;
                }
                partyManager.invitePlayer(player, target);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(configManager.format("§6Usage: /party accept <id>"));
                    return true;
                }
                try {
                    partyManager.acceptInvite(player, java.util.UUID.fromString(args[1]));
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(configManager.format("§cInvalid invite id."));
                }
            }
            case "deny" -> {
                if (args.length < 2) {
                    player.sendMessage(configManager.format("§6Usage: /party deny <id>"));
                    return true;
                }
                try {
                    partyManager.denyInvite(player, java.util.UUID.fromString(args[1]));
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(configManager.format("§cInvalid invite id."));
                }
            }
            case "leave" -> partyManager.leaveParty(player);
            case "disband" -> partyManager.disbandParty(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(configManager.format("§6Usage: /party kick <player>"));
                    return true;
                }
                partyManager.kickPlayer(player, args[1]);
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(configManager.format("§6Usage: /party join <leader>"));
                    return true;
                }
                partyManager.joinParty(player, args[1]);
            }
            case "settings" -> partyManager.openSettingsMenu(player);
            case "teams" -> partyManager.openTeamMenu(player);
            case "ready" -> {
                Party party = partyManager.getParty(player);
                if (party == null) {
                    player.sendMessage(configManager.format("§cYou are not in a party."));
                    return true;
                }
                dev.divsersmp.duels.gui.ModernReadyGUI.open(player, party);
            }
            case "start" -> {
                Party party = partyManager.getParty(player);
                if (party == null) {
                    player.sendMessage(configManager.format("§cYou are not in a party."));
                    return true;
                }
                if (!party.isLeader(player.getUniqueId())) {
                    player.sendMessage(configManager.format("§cOnly the leader can start the duel."));
                    return true;
                }
                if (partyManager.startPartyDuel(party)) {
                    player.sendMessage(configManager.format("§aThe team duel is starting."));
                } else {
                    player.sendMessage(configManager.format("§cThe party duel could not start."));
                }
            }
            default -> player.sendMessage(configManager.format("§6Unknown party subcommand."));
        }
        } catch (Exception ex) {
            pluginLog("Error executing /party command: " + ex.getMessage());
            player.sendMessage(configManager.format("§cAn unexpected error occurred while executing that command."));
            return true;
        }
        return true;
    }

    private void pluginLog(String msg) {
        try {
            // Use Bukkit logger
            org.bukkit.Bukkit.getLogger().warning(msg);
        } catch (Exception ignored) {}
    }
}

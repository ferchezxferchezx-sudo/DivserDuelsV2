package dev.divsersmp.duels;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DivserDuelsCommand implements CommandExecutor {
    private final DivserDuelsXYZ plugin;

    public DivserDuelsCommand(DivserDuelsXYZ plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6DivserDuels Plugin");
            sender.sendMessage("§7Usage: /divserduels reload");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("divserduels.reload")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            // Reload guis.yml
            plugin.getGuisConfigManager().reloadGuisConfig();
            sender.sendMessage("§a✓ guis.yml reloaded successfully!");
            
            // Optionally reload config.yml too
            plugin.getConfigManager().loadConfig();
            sender.sendMessage("§a✓ config.yml reloaded successfully!");
            
            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use /divserduels reload");
        return true;
    }
}

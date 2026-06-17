package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpectateCommand implements CommandExecutor {
    private final DuelManager duelManager;
    private final ConfigManager configManager;

    public SpectateCommand(DuelManager duelManager, ConfigManager configManager) {
        this.duelManager = duelManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getErrorPlayerOnly());
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(configManager.format("§6Usage: /spectate <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(configManager.getErrorPlayerNotFound());
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(configManager.format("§cYou cannot spectate yourself."));
            return true;
        }

        DuelSession session = duelManager.getSession(target);
        if (session == null) {
            player.sendMessage(configManager.format("§cThat player is not currently in a duel."));
            return true;
        }

        Location spectatorLocation = null;
        Arena arena = duelManager.getArenaManager().getArena(session.getRequest().getArenaName());
        if (arena != null && arena.hasSpectatorSpawn()) {
            spectatorLocation = arena.getSpectatorSpawn();
        }
        if (spectatorLocation == null && session.getArenaCenter() != null) {
            spectatorLocation = session.getArenaCenter().clone().add(0, 1, 0);
        }

        if (spectatorLocation != null) {
            player.teleport(spectatorLocation);
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(target);
        player.sendMessage(configManager.format("§aNow spectating §e" + target.getName()));
        return true;
    }
}

package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Arrays;

public class ArenaCommand implements CommandExecutor {
    private final DuelManager duelManager;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;

    public ArenaCommand(DuelManager duelManager, ArenaManager arenaManager, ConfigManager configManager) {
        this.duelManager = duelManager;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(configManager.format("§6Usage: /arena|/duelsarena <create|delete|list|setspawn|setspectator|seticon|wand|worldborder wand|saveworldborder|schematicwand|saveschematic>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena create <name>"));
                return true;
            }
            String arenaName = args[1];
            if (arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getErrorArenaExists());
                return true;
            }
            arenaManager.createArena(arenaName, player.getLocation());
            player.sendMessage(configManager.getArenaCreated(arenaName));
            if (configManager.isSoundLevelUpEnabled()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, configManager.getLevelUpPitch());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("delete")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena delete <name>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            arenaManager.deleteArena(arenaName);
            sender.sendMessage(configManager.getArenaDeleted(arenaName));
            if (sender instanceof Player player && configManager.isSoundDenyEnabled()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, configManager.getDenySoundPitch());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            Collection<String> arenaList = arenaManager.listArenas();
            if (arenaList.isEmpty()) {
                sender.sendMessage(configManager.getMessage("status_no_arenas"));
                return true;
            }
            sender.sendMessage(configManager.format("§6Available Arenas: §e" + String.join("§7, §e", arenaList)));
            return true;
        }

        if (args[0].equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena setspawn <1|2> <arena>"));
                return true;
            }
            String spawnStr = args[1];
            if (!spawnStr.equals("1") && !spawnStr.equals("2") && !spawnStr.equalsIgnoreCase("lime") && !spawnStr.equalsIgnoreCase("red")) {
                player.sendMessage(configManager.format("§cSpawn must be 1, 2, lime, or red."));
                return true;
            }
            String arenaName = args[2];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            int spawnNumber = spawnStr.equalsIgnoreCase("lime") ? 1 : spawnStr.equalsIgnoreCase("red") ? 2 : Integer.parseInt(spawnStr);
            arenaManager.setSpawn(arenaName, spawnNumber, player.getLocation());
            player.sendMessage(configManager.format("§aSpawn " + spawnNumber + " set for arena §e" + arenaName));
            if (configManager.isSoundLevelUpEnabled()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, configManager.getLevelUpPitch());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("setspectator")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena setspectator <arena>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            arenaManager.setSpectatorSpawn(arenaName, player.getLocation());
            player.sendMessage(configManager.format("§aSpectator spawn set for arena §e" + arenaName));
            if (configManager.isSoundLevelUpEnabled()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, configManager.getLevelUpPitch());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("setcorner")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena setcorner <1|2> <arena>"));
                return true;
            }
            String corner = args[1];
            if (!corner.equals("1") && !corner.equals("2")) {
                player.sendMessage(configManager.format("§cCorner must be 1 or 2."));
                return true;
            }
            String arenaName = args[2];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            int cnum = Integer.parseInt(corner);
            arenaManager.setCorner(arenaName, cnum, player.getLocation());
            player.sendMessage(configManager.format("§aCorner " + cnum + " set for arena §e" + arenaName));
            return true;
        }

        if (args[0].equalsIgnoreCase("setregen")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena setregen <arena> <on|off>"));
                return true;
            }
            String arenaName = args[1];
            String onoff = args[2];
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            boolean on = onoff.equalsIgnoreCase("on") || onoff.equalsIgnoreCase("true");
            arenaManager.setAutoRegenerate(arenaName, on);
            sender.sendMessage(configManager.format("§aAuto-regeneration for §e" + arenaName + " §aset to §e" + (on ? "ON" : "OFF")));
            return true;
        }

        if (args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("regernate")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena regenerate <arena>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            boolean ok = arenaManager.regenerateNow(arenaName);
            if (ok) sender.sendMessage(configManager.format("§aArena §e" + arenaName + " §ahas been regenerated."));
            else sender.sendMessage(configManager.format("§cFailed to regenerate arena §e" + arenaName + "§c."));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            // Reload config and arenas
            configManager.loadConfig();
            arenaManager.enable();
            sender.sendMessage(configManager.format("§aDivserDuelsXYZ reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("worldborder") || args[0].equalsIgnoreCase("worldborderwand")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("wand")) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena worldborder wand <arena>"));
                return true;
            }
            String arenaName = args[2];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            giveSelectionWand(player, arenaName, "WorldBorder", org.bukkit.ChatColor.GOLD + "WorldBorder Wand: " + org.bukkit.ChatColor.YELLOW + arenaName);
            return true;
        }

        if (args[0].equalsIgnoreCase("protect")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("wand")) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena protect wand <arena>"));
                return true;
            }
            String arenaName = args[2];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            giveProtectWand(player, arenaName);
            return true;
        }

        if (args[0].equalsIgnoreCase("saveworldborder") || (args[0].equalsIgnoreCase("save") && args.length > 1 && args[1].equalsIgnoreCase("worldborder"))) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            int index = args[0].equalsIgnoreCase("saveworldborder") ? 1 : 2;
            if (args.length <= index) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena saveworldborder <arena>"));
                return true;
            }
            String arenaName = args[index];
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            Arena arena = arenaManager.getArena(arenaName);
            if (arena == null || !arena.hasWorldBorderCorners()) {
                sender.sendMessage(configManager.format("§cThis arena must have both worldborder corners set first."));
                return true;
            }
            if (arenaManager.saveWorldBorder(arena)) {
                sender.sendMessage(configManager.format("§aWorld border saved for arena §e" + arenaName));
            } else {
                sender.sendMessage(configManager.format("§cFailed to save world border for arena §e" + arenaName + "§c."));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("schematicwand") || (args[0].equalsIgnoreCase("schematic") && args.length > 1 && args[1].equalsIgnoreCase("wand"))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            int index = args[0].equalsIgnoreCase("schematicwand") ? 1 : 2;
            if (args.length <= index) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena schematicwand <arena>"));
                return true;
            }
            String arenaName = args[index];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            giveSelectionWand(player, arenaName, "Schematic", org.bukkit.ChatColor.GOLD + "Schematic Wand: " + org.bukkit.ChatColor.YELLOW + arenaName);
            return true;
        }

        if (args[0].equalsIgnoreCase("wand")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena wand <arena>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            giveSelectionWand(player, arenaName, "Duels Wand: ", org.bukkit.ChatColor.GOLD + "Duels Wand: " + org.bukkit.ChatColor.YELLOW + arenaName);
            return true;
        }

        if (args[0].equalsIgnoreCase("setprefix")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena setprefix <prefix>"));
                return true;
            }
            String prefix = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            configManager.setPrefix(prefix);
            sender.sendMessage(configManager.format("§aPrefix updated to: " + prefix));
            return true;
        }

        if (args[0].equalsIgnoreCase("seticon")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getErrorPlayerOnly());
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(configManager.format("§6Usage: /duelsarena seticon <arena> <material>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                player.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            String materialName = args[2].toUpperCase();
            try {
                org.bukkit.Material icon = org.bukkit.Material.valueOf(materialName);
                arenaManager.setIcon(arenaName, icon);
                player.sendMessage(configManager.format("§aIcon for arena §e" + arenaName + " §aset to §e" + icon.name()));
            } catch (IllegalArgumentException ex) {
                player.sendMessage(configManager.format("§cUnknown material: " + materialName));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("saveschematic")) {
            if (!sender.hasPermission("divsersmp.duels.admin")) {
                sender.sendMessage(configManager.getErrorNoPermission());
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(configManager.format("§6Usage: /duelsarena saveschematic <arena>"));
                return true;
            }
            String arenaName = args[1];
            if (!arenaManager.arenaExists(arenaName)) {
                sender.sendMessage(configManager.getArenaNotFound());
                return true;
            }
            Arena arena = arenaManager.getArena(arenaName);
            if (arena == null || !arena.hasCorners()) {
                sender.sendMessage(configManager.format("§cThis arena must have both corners set first."));
                return true;
            }
            if (arenaManager.saveSchematic(arena)) {
                sender.sendMessage(configManager.format("§aSchematic saved for arena §e" + arenaName + "§a. Use /duelsarena setregen " + arenaName + " on to enable automatic regeneration."));
            } else {
                sender.sendMessage(configManager.format("§cFailed to save schematic for arena §e" + arenaName + "§c. Make sure WorldEdit is installed and working."));
            }
            return true;
        }

        sender.sendMessage(configManager.format("§6Usage: /arena|/duelsarena <create|delete|list|setspawn|setspectator|seticon|wand|worldborder wand|saveworldborder|schematicwand|saveschematic>"));
        return true;
    }

    private void giveSelectionWand(Player player, String arenaName, String wandType, String displayName) {
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_ROD);
        org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(displayName);
        meta.setLore(Arrays.asList(org.bukkit.ChatColor.GRAY + "Left-click to set corner 1", org.bukkit.ChatColor.GRAY + "Right-click to set corner 2", org.bukkit.ChatColor.DARK_GRAY + "Type: " + wandType));
        // store arena name in persistent data container for reliable retrieval
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ");
        if (plugin != null) {
            NamespacedKey key = new NamespacedKey(plugin, "duels_arena");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, arenaName);
        }
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(configManager.format("§6✨ §a" + wandType + " wand given for arena §e" + arenaName + " §7- use it on a block to set both corners."));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
    }

    private void giveProtectWand(Player player, String arenaName) {
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_ROD);
        org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(org.bukkit.ChatColor.GOLD + "Protect Wand: " + org.bukkit.ChatColor.YELLOW + arenaName);
        meta.setLore(Arrays.asList(org.bukkit.ChatColor.GRAY + "Left-click: add protect block", org.bukkit.ChatColor.GRAY + "Right-click: remove protect block", org.bukkit.ChatColor.DARK_GRAY + "Protects blocks during duels"));
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ");
        if (plugin != null) {
            NamespacedKey key = new NamespacedKey(plugin, "duels_protect");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, arenaName);
        }
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(configManager.format("§6✨ §aProtect wand given for arena §e" + arenaName));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
    }
}

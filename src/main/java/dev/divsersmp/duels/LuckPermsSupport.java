package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class LuckPermsSupport {
    private final JavaPlugin plugin;
    private final boolean available;

    public LuckPermsSupport(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = detectLuckPerms();
    }

    private boolean detectLuckPerms() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return false;
            }
            if (plugin.getServer().getPluginCommand("lp") == null && plugin.getServer().getPluginCommand("luckperms") == null) {
                return false;
            }
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getPrimaryGroup(UUID playerId) {
        if (!available || playerId == null) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            if (luckPerms == null) {
                return null;
            }

            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Method getUserManager = luckPermsClass.getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            if (userManager == null) {
                return null;
            }

            Class<?> userManagerClass = Class.forName("net.luckperms.api.model.user.UserManager");
            Method loadUser = userManagerClass.getMethod("loadUser", UUID.class);
            Object future = loadUser.invoke(userManager, playerId);
            if (!(future instanceof CompletableFuture<?>)) {
                return null;
            }

            Object user = ((CompletableFuture<?>) future).get();
            if (user == null) {
                return null;
            }

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method getPrimaryGroup = userClass.getMethod("getPrimaryGroup");
            return (String) getPrimaryGroup.invoke(user);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "LuckPerms support failed: " + ex.getMessage());
            return null;
        }
    }

    public boolean setPrimaryGroup(UUID playerId, String group) {
        if (!available || playerId == null || group == null || group.isBlank()) {
            return false;
        }
        try {
            String playerIdentifier = null;
            org.bukkit.entity.Player online = Bukkit.getPlayer(playerId);
            if (online != null && online.isOnline()) {
                playerIdentifier = online.getName();
            }
            if (playerIdentifier == null) {
                playerIdentifier = Bukkit.getOfflinePlayer(playerId).getName();
            }
            if (playerIdentifier == null || playerIdentifier.isBlank()) {
                playerIdentifier = playerId.toString();
            }
            String command = "lp user " + playerIdentifier + " parent set " + group;
            return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        } catch (Exception ex) {
            plugin.getLogger().warning("LuckPerms setPrimaryGroup failed: " + ex.getMessage());
            return false;
        }
    }
}

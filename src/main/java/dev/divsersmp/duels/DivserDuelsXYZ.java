package dev.divsersmp.duels;

import org.bukkit.plugin.java.JavaPlugin;

public final class DivserDuelsXYZ extends JavaPlugin {
    private ConfigManager configManager;
    private GuisConfigManager guisConfigManager;
    private DuelManager duelManager;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();
        this.guisConfigManager = new GuisConfigManager(this);
        this.guisConfigManager.loadGuisConfig();
        this.arenaManager = new ArenaManager(this);
        this.arenaManager.enable();
        // ensure editable config resources are present in plugin folder
        try {
            saveResource("scoreboard.yml", false);
            saveResource("title.yml", false);
            saveResource("partycreate.yml", false);
        } catch (Exception ignored) {}

        this.duelManager = new DuelManager(this, arenaManager, configManager);
        this.duelManager.enable();
        
        // Register commands
        getCommand("divserduels").setExecutor(new DivserDuelsCommand(this));
    }

    @Override
    public void onDisable() {
        if (this.duelManager != null) {
            this.duelManager.shutdown();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GuisConfigManager getGuisConfigManager() {
        return guisConfigManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public DuelManager getPartyDuelManager() {
        return duelManager;
    }
}

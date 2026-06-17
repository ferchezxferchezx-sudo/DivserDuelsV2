package dev.divsersmp.duels;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GuisConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration guisConfig;
    private File guisFile;

    public GuisConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadGuisConfig() {
        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        guisFile = new File(plugin.getDataFolder(), "guis.yml");
        if (!guisFile.exists()) {
            plugin.saveResource("guis.yml", false);
        }

        guisConfig = YamlConfiguration.loadConfiguration(guisFile);

        // Load defaults from the bundled guis.yml
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("guis.yml"), StandardCharsets.UTF_8)) {
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            guisConfig.setDefaults(defaultConfig);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default guis config: " + e.getMessage());
        }
    }

    public void reloadGuisConfig() {
        loadGuisConfig();
    }

    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    // ========== GUI CONFIGURATION ACCESSORS ==========

    public String getItemName(String guiName, String itemKey) {
        return guisConfig.getString(guiName + "." + itemKey + ".name", itemKey);
    }

    public String getItemMaterial(String guiName, String itemKey) {
        return guisConfig.getString(guiName + "." + itemKey + ".material", "STONE");
    }

    public String getItemLore(String guiName, String itemKey) {
        return guisConfig.getString(guiName + "." + itemKey + ".lore", "");
    }

    public String getItemClickCommand(String guiName, String itemKey) {
        return guisConfig.getString(guiName + "." + itemKey + ".click-command", "");
    }

    public int getItemSlot(String guiName, String itemKey) {
        return guisConfig.getInt(guiName + "." + itemKey + ".slot", -1);
    }

    public String getGuiTitle(String guiName) {
        return guisConfig.getString(guiName + ".title", guiName);
    }

    public int getGuiSize(String guiName) {
        return guisConfig.getInt(guiName + ".size", 27);
    }
}

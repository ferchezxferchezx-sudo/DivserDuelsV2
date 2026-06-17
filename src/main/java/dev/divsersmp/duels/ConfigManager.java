package dev.divsersmp.duels;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatColor;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration titleConfig;
    private FileConfiguration partyCreateConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // Create config file if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load defaults from the bundled config.yml
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            config.setDefaults(defaultConfig);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default config: " + e.getMessage());
        }

        loadTitleConfig();
        loadPartyCreateConfig();
    }

    private void loadTitleConfig() {
        try {
            File titleFile = new File(plugin.getDataFolder(), "title.yml");
            if (!titleFile.exists()) {
                plugin.saveResource("title.yml", false);
            }
            titleConfig = YamlConfiguration.loadConfiguration(titleFile);
            try (InputStreamReader reader = new InputStreamReader(
                    plugin.getResource("title.yml"), StandardCharsets.UTF_8)) {
                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
                titleConfig.setDefaults(defaultConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load title config: " + e.getMessage());
        }
    }

    private void loadPartyCreateConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "partycreate.yml");
            if (!configFile.exists()) {
                plugin.saveResource("partycreate.yml", false);
            }
            partyCreateConfig = YamlConfiguration.loadConfiguration(configFile);
            try (InputStreamReader reader = new InputStreamReader(
                    plugin.getResource("partycreate.yml"), StandardCharsets.UTF_8)) {
                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
                partyCreateConfig.setDefaults(defaultConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load party create config: " + e.getMessage());
        }
    }

    // ========== MESSAGE ACCESSORS ==========
    public String getMessage(String key, Object... replacements) {
        String message = config.getString("messages." + key, "");
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1].toString());
            }
        }
        return format(message);
    }

    public String getTitle(String key, Object... replacements) {
        if (titleConfig == null) {
            return "";
        }
        String message = titleConfig.getString("titles." + key, "");
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1].toString());
            }
        }
        return translateGradients(message);
    }

    public String getTitleRaw(String key) {
        if (titleConfig == null) {
            return "";
        }
        return translateGradients(titleConfig.getString("titles." + key, ""));
    }

    public FileConfiguration getPartyCreateConfig() {
        return partyCreateConfig;
    }

    public long getPartyInviteCooldownMs() {
        if (partyCreateConfig == null) {
            return 1000L;
        }
        return partyCreateConfig.getLong("partycreate.cooldown_ms", 1000L);
    }

    public ItemStack createPartyItem(String itemKey) {
        if (partyCreateConfig == null) {
            return null;
        }
        String path = "partycreate." + itemKey;
        String materialName = partyCreateConfig.getString(path + ".material", "IRON_SWORD");
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException ex) {
            material = Material.IRON_SWORD;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(getTitleFromPartyCreate(path + ".display_name", material));
        List<String> lore = partyCreateConfig.getStringList(path + ".lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String getTitleFromPartyCreate(String path, Material fallback) {
        if (partyCreateConfig == null) {
            return fallback.name();
        }
        String title = partyCreateConfig.getString(path, null);
        return title != null ? translateGradients(title) : fallback.name();
    }

    public int getPartyItemSlot(String itemKey, int defaultSlot) {
        if (partyCreateConfig == null) {
            return defaultSlot;
        }
        return partyCreateConfig.getInt("partycreate." + itemKey + ".slot", defaultSlot);
    }

    public String getPartyCreateSound(String key, String defaultSound) {
        if (partyCreateConfig == null) {
            return defaultSound;
        }
        return partyCreateConfig.getString("partycreate.sounds." + key, defaultSound);
    }

    public String getRawMessage(String key) {
        return translateGradients(config.getString("messages." + key, ""));
    }

    public String getPrefix() {
        return config.getString("messages.prefix", "§7[§bDivserDuels§7] §r");
    }

    public String format(String message) {
        if (message == null || message.isEmpty()) {
            return getPrefix().trim();
        }
        String prefix = getPrefix();
        String formatted = message.startsWith(prefix) ? message : prefix + message;
        return translateGradients(formatted);
    }

    private String translateGradients(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile("<gradient:([#A-Fa-f0-9]{6}),([#A-Fa-f0-9]{6})>(.*?)</gradient>");
        Matcher matcher = pattern.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String start = matcher.group(1);
            String end = matcher.group(2);
            String text = matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(applyGradient(start, end, text)));
        }
        matcher.appendTail(result);
        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }

    public void setPrefix(String prefix) {
        config.set("messages.prefix", prefix);
        saveConfig();
    }

    public void saveConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            if (!configFile.exists()) {
                configFile.createNewFile();
            }
            ((YamlConfiguration) config).save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    public String applyGradient(String startHex, String endHex, String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            Color start = Color.decode(startHex.startsWith("#") ? startHex : "#" + startHex);
            Color end = Color.decode(endHex.startsWith("#") ? endHex : "#" + endHex);
            StringBuilder out = new StringBuilder();
            char[] chars = text.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                double ratio = chars.length == 1 ? 0.0 : (double) i / (chars.length - 1);
                int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * ratio);
                int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * ratio);
                int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * ratio);
                String hex = String.format("#%02x%02x%02x", r, g, b);
                out.append(net.md_5.bungee.api.ChatColor.of(hex)).append(chars[i]);
            }
            return out.toString();
        } catch (Exception e) {
            return text;
        }
    }

    // Arena messages
    public String getArenaCreated(String arena) {
        return getMessage("arena_created", "arena", arena);
    }

    public String getArenaDeleted(String arena) {
        return getMessage("arena_deleted", "arena", arena);
    }

    public String getArenaCenterSelected(int x, int y, int z) {
        return getMessage("arena_center_selected", "x", x, "y", y, "z", z);
    }

    public String getArenaSelected(String arena) {
        return getMessage("arena_selected", "arena", arena);
    }

    public String getArenaNotFound() {
        return config.getString("messages.arena_not_found", "");
    }

    // Duel request messages
    public String getDuelRequestSent(String target) {
        return getMessage("duel_request_sent", "target", target);
    }

    public String getDuelRequestBody(String player) {
        return getMessage("duel_request_body", "player", player);
    }

    public String getDuelAllowed(String rules) {
        return getMessage("duel_allowed", "rules", rules);
    }

    public String getDuelArenaLocation(int x, int y, int z) {
        return getMessage("duel_arena_location", "x", x, "y", y, "z", z);
    }

    public String getDuelRequestExpired(String target) {
        return getMessage("duel_request_expired", "target", target);
    }

    public String getDuelDeniedTarget(String target) {
        return getMessage("duel_denied_target", "target", target);
    }

    public String getDuelDenied() {
        return config.getString("messages.duel_denied", "");
    }

    // Error messages
    public String getErrorPlayerOnly() {
        return config.getString("messages.error_player_only", "");
    }

    public String getErrorNoPermission() {
        return config.getString("messages.error_no_permission", "");
    }

    public String getErrorInvalidRequestId() {
        return config.getString("messages.error_invalid_request_id", "");
    }

    public String getErrorRequestExpired() {
        return config.getString("messages.error_request_expired", "");
    }

    public String getErrorNotTarget() {
        return config.getString("messages.error_not_target", "");
    }

    public String getErrorAlreadyInDuel() {
        return config.getString("messages.error_already_in_duel", "");
    }

    public String getErrorArenaNotSelected() {
        return config.getString("messages.error_arena_not_selected", "");
    }

    public String getErrorArenaNotSet() {
        return config.getString("messages.error_arena_not_set", "");
    }

    public String getErrorArenaMissing() {
        return config.getString("messages.error_arena_missing", "");
    }

    public String getErrorCantDuelSelf() {
        return config.getString("messages.error_cant_duel_self", "");
    }

    public String getErrorArenaExists() {
        return config.getString("messages.error_arena_exists", "");
    }

    public String getErrorItemBlocked() {
        return config.getString("messages.error_item_blocked", "");
    }

    public String getErrorPotionsBlocked() {
        return config.getString("messages.error_potions_blocked", "");
    }

    public String getErrorSlowFallBlocked() {
        return config.getString("messages.error_slow_fall_blocked", "");
    }

    public String getErrorNoActiveSetup() {
        return config.getString("messages.error_no_active_setup", "");
    }

    public String getErrorPlayerNotFound() {
        return config.getString("messages.error_player_not_found", "");
    }

    // Menu strings
    public String getMenuArenaSelection() {
        return config.getString("messages.menu_arena_selection", "");
    }

    public String getMenuKeepInventory() {
        return config.getString("messages.menu_keep_inventory", "");
    }

    public String getMenuRestrictions() {
        return config.getString("messages.menu_restrictions", "");
    }

    public String getItemSelectArena() {
        return config.getString("messages.item_select_arena", "");
    }

    public String getItemCustomArena() {
        return config.getString("messages.menu_custom_arena", "");
    }

    public String getMenuSelectArenaBlock() {
        return config.getString("messages.menu_select_arena_block", "");
    }

    public String getStatusOn() {
        return config.getString("messages.status_on", "");
    }

    public String getStatusOff() {
        return config.getString("messages.status_off", "");
    }

    // ========== DUEL SETTINGS ==========
    public boolean getDefaultKeepInventory() {
        return config.getBoolean("duel.default_keep_inventory", false);
    }

    public boolean getDefaultBlockArrows() {
        return config.getBoolean("duel.default_block_arrows", true);
    }

    public boolean getDefaultBlockFireworks() {
        return config.getBoolean("duel.default_block_fireworks", true);
    }

    public boolean getDefaultBlockPotions() {
        return config.getBoolean("duel.default_block_potions", true);
    }

    public boolean getDefaultBlockSlowFalling() {
        return config.getBoolean("duel.default_block_slow_falling", true);
    }

    public long getRequestExpiration() {
        return config.getLong("duel.request_expiration", 60) * 20L; // Convert to ticks
    }

    public int getBorderRadius() {
        return config.getInt("duel.border_radius", 50);
    }

    public double getBorderDamage() {
        return config.getDouble("duel.border_damage", 5.0);
    }

    // ========== SOUND SETTINGS ==========
    public boolean isSoundButtonClickEnabled() {
        return config.getBoolean("sounds.button_click", true);
    }

    public float getButtonClickPitch() {
        return (float) config.getDouble("sounds.button_click_pitch", 1.0);
    }

    public boolean isSoundLevelUpEnabled() {
        return config.getBoolean("sounds.level_up", true);
    }

    public float getLevelUpPitch() {
        return (float) config.getDouble("sounds.level_up_pitch", 1.3);
    }

    public boolean isSoundDenyEnabled() {
        return config.getBoolean("sounds.deny_sound", true);
    }

    public float getDenySoundPitch() {
        return (float) config.getDouble("sounds.deny_sound_pitch", 0.8);
    }

    public boolean isSoundPickupEnabled() {
        return config.getBoolean("sounds.pickup_sound", true);
    }

    public float getPickupSoundPitch() {
        return (float) config.getDouble("sounds.pickup_sound_pitch", 1.5);
    }

    public boolean isSoundArrowHitEnabled() {
        return config.getBoolean("sounds.arrow_hit", true);
    }

    public float getArrowHitPitch() {
        return (float) config.getDouble("sounds.arrow_hit_pitch", 1.0);
    }

    public boolean isDuelEndSoundEnabled() {
        return config.getBoolean("duel_end.sound_enabled", true);
    }

    public String getDuelEndWinnerSound() {
        return config.getString("duel_end.winner_sound", "ENTITY_PLAYER_LEVELUP");
    }

    public String getDuelEndLoserSound() {
        return config.getString("duel_end.loser_sound", "ENTITY_WITHER_HURT");
    }

    public float getDuelEndSoundPitch() {
        return (float) config.getDouble("duel_end.sound_pitch", 1.3);
    }

    public int getDuelEndBlindnessDuration() {
        return config.getInt("duel_end.blindness_duration", 3);
    }

    public String getDuelEndWinnerTitle() {
        return config.getString("duel_end.winner_title", "§6✨ VICTORY ✨");
    }

    public String getDuelEndWinnerSubtitle() {
        return config.getString("duel_end.winner_subtitle", "§7You defeated §e{loser}§7!");
    }

    public String getDuelEndLoserTitle() {
        return config.getString("duel_end.loser_title", "§c✗ DEFEAT ✗");
    }

    public String getDuelEndLoserSubtitle() {
        return config.getString("duel_end.loser_subtitle", "§7Better luck next time §e{winner}§7!");
    }

    // ========== PARTICLE SETTINGS ==========
    public boolean isArenaCenterParticlesEnabled() {
        return config.getBoolean("particles.arena_center_particles", true);
    }

    public int getParticleCount() {
        return config.getInt("particles.particle_count", 50);
    }
}

package dev.divsersmp.duels;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DuelManager implements Listener {
    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final PartyManager partyManager;
    private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, DuelRequest> openConfig = new ConcurrentHashMap<>();
    private final Map<UUID, DuelSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRatings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> leaderboard = new ConcurrentHashMap<>();
    private final NamespacedKey arenaNameKey;
    private final ScoreboardService scoreboardService;
    private final LuckPermsSupport luckPermsSupport;
    private File leaderboardFile;
    private FileConfiguration leaderboardConfig;

    public DuelManager(JavaPlugin plugin, ArenaManager arenaManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
        this.partyManager = new PartyManager(this, arenaManager, configManager);
        this.scoreboardService = new ScoreboardService(plugin);
        this.luckPermsSupport = new LuckPermsSupport(plugin);
        this.arenaNameKey = new NamespacedKey(plugin, "duel_arena_name");
    }

    private File getBackupFolder() {
        File dir = new File(plugin.getDataFolder(), "duel_backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File getBackupFile(UUID playerId) {
        return new File(getBackupFolder(), playerId.toString() + ".yml");
    }

    private void saveInventoryBackup(Player player) {
        try {
            if (player == null) return;
            File file = getBackupFile(player.getUniqueId());
            org.bukkit.configuration.file.FileConfiguration cfg = new YamlConfiguration();
            cfg.set("contents", Arrays.asList(player.getInventory().getContents()));
            cfg.set("armor", Arrays.asList(player.getInventory().getArmorContents()));
            cfg.set("extra", Arrays.asList(player.getInventory().getExtraContents()));
            Location loc = player.getLocation();
            if (loc != null) {
                cfg.set("returnLocation.world", loc.getWorld().getName());
                cfg.set("returnLocation.x", loc.getX());
                cfg.set("returnLocation.y", loc.getY());
                cfg.set("returnLocation.z", loc.getZ());
                cfg.set("returnLocation.yaw", loc.getYaw());
                cfg.set("returnLocation.pitch", loc.getPitch());
            }
            cfg.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save duel inventory backup: " + ex.getMessage());
        }
    }

    private void restoreInventoryBackup(Player player) {
        try {
            if (player == null) return;
            File file = getBackupFile(player.getUniqueId());
            if (!file.exists()) return;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<ItemStack> contents = (List<ItemStack>) cfg.getList("contents", new ArrayList<>());
            List<ItemStack> armor = (List<ItemStack>) cfg.getList("armor", new ArrayList<>());
            List<ItemStack> extra = (List<ItemStack>) cfg.getList("extra", new ArrayList<>());
            if (!contents.isEmpty()) {
                ItemStack[] arr = contents.toArray(new ItemStack[0]);
                player.getInventory().setContents(arr);
            }
            if (!armor.isEmpty()) {
                ItemStack[] arr = armor.toArray(new ItemStack[0]);
                player.getInventory().setArmorContents(arr);
            }
            if (!extra.isEmpty()) {
                ItemStack[] arr = extra.toArray(new ItemStack[0]);
                player.getInventory().setExtraContents(arr);
            }
            if (cfg.contains("returnLocation.world")) {
                String world = cfg.getString("returnLocation.world");
                double x = cfg.getDouble("returnLocation.x", player.getLocation().getX());
                double y = cfg.getDouble("returnLocation.y", player.getLocation().getY());
                double z = cfg.getDouble("returnLocation.z", player.getLocation().getZ());
                float yaw = (float) cfg.getDouble("returnLocation.yaw", player.getLocation().getYaw());
                float pitch = (float) cfg.getDouble("returnLocation.pitch", player.getLocation().getPitch());
                org.bukkit.World w = Bukkit.getWorld(world);
                if (w != null) {
                    Location ret = new Location(w, x, y, z, yaw, pitch);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(ret), 2L);
                }
            }
            // delete backup after restore
            try { file.delete(); } catch (Exception ignored) {}
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not restore duel inventory backup: " + ex.getMessage());
        }
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("duel").setExecutor(new DuelCommand(this, configManager));
        ArenaCommand arenaCommand = new ArenaCommand(this, arenaManager, configManager);
        if (plugin.getCommand("duelsarena") != null) {
            plugin.getCommand("duelsarena").setExecutor(arenaCommand);
        }
        if (plugin.getCommand("arena") != null) {
            plugin.getCommand("arena").setExecutor(arenaCommand);
        }
        if (plugin.getCommand("party") != null) {
            plugin.getCommand("party").setExecutor(new PartyCommand(partyManager, configManager));
        }
        if (plugin.getCommand("spectate") != null) {
            plugin.getCommand("spectate").setExecutor(new SpectateCommand(this, configManager));
        }
        ensureLeaderboardFile();
        loadLeaderboard();
    }

    public void shutdown() {
        activeSessions.values().forEach(DuelSession::endDuelQuietly);
        activeSessions.clear();
    }

    public void openConfigurationMenu(Player challenger, Player target) {
        DuelRequest request = new DuelRequest(challenger.getUniqueId(), target.getUniqueId(), challenger.getName(), target.getName());
        // Set defaults from config
        request.setKeepInventory(configManager.getDefaultKeepInventory());
        request.setBlockArrows(configManager.getDefaultBlockArrows());
        request.setBlockFireworks(configManager.getDefaultBlockFireworks());
        request.setBlockPotions(configManager.getDefaultBlockPotions());
        request.setBlockSlowFalling(configManager.getDefaultBlockSlowFalling());
        openConfig.put(challenger.getUniqueId(), request);
        challenger.openInventory(createArenaSelectionMenu(request));
        if (configManager.isSoundButtonClickEnabled()) {
            challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, configManager.getButtonClickPitch());
        }
    }

    public void acceptRequest(Player accepter, UUID requestId) {
        DuelRequest request = pendingRequests.get(requestId);
        if (request == null) {
            accepter.sendMessage(configManager.getErrorRequestExpired());
            return;
        }
        if (!request.getTarget().equals(accepter.getUniqueId())) {
            accepter.sendMessage(configManager.getErrorNotTarget());
            return;
        }
        Player challenger = Bukkit.getPlayer(request.getChallenger());
        if (challenger == null || !challenger.isOnline()) {
            accepter.sendMessage(configManager.getMessage("duel_challenger_offline"));
            return;
        }
        if (request.getArenaCenter() == null) {
            accepter.sendMessage(configManager.getErrorArenaMissing());
            challenger.sendMessage(configManager.getErrorArenaNotSet());
            return;
        }
        if (request.getArenaName() != null && isArenaInUse(request.getArenaName())) {
            accepter.sendMessage(configManager.format("§cThis arena is currently in use. Please choose a different arena."));
            challenger.sendMessage(configManager.format("§cYour duel arena is now occupied. Please select another arena and send a new request."));
            pendingRequests.remove(requestId);
            return;
        }
        if (activeSessions.containsKey(challenger.getUniqueId()) || activeSessions.containsKey(accepter.getUniqueId())) {
            accepter.sendMessage(configManager.getErrorAlreadyInDuel());
            return;
        }
        pendingRequests.remove(requestId);
        startDuel(request, challenger, accepter);
    }

    public void denyRequest(Player denier, UUID requestId) {
        DuelRequest request = pendingRequests.get(requestId);
        if (request == null) {
            denier.sendMessage(configManager.getErrorRequestExpired());
            return;
        }
        if (!request.getTarget().equals(denier.getUniqueId())) {
            denier.sendMessage(configManager.getErrorNotTarget());
            return;
        }
        pendingRequests.remove(requestId);
        Player challenger = Bukkit.getPlayer(request.getChallenger());
        if (challenger != null && challenger.isOnline()) {
            challenger.sendMessage(configManager.getDuelDeniedTarget(denier.getName()));
            if (configManager.isSoundDenyEnabled()) {
                challenger.playSound(challenger.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.2f, configManager.getDenySoundPitch());
            }
        }
        denier.sendMessage(configManager.getDuelDenied());
    }

    public void setArenaCenter(Player player, Location location) {
        DuelRequest request = openConfig.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        Location center = location.clone().add(0.5, 1, 0.5);
        request.setArenaCenter(center);
        player.sendMessage(configManager.getArenaCenterSelected(center.getBlockX(), center.getBlockY(), center.getBlockZ()));
        if (configManager.isSoundLevelUpEnabled()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, configManager.getLevelUpPitch());
        }
        if (configManager.isArenaCenterParticlesEnabled()) {
            player.spawnParticle(org.bukkit.Particle.END_ROD, center, configManager.getParticleCount(), 1, 1, 1, 0.1);
        }
        player.openInventory(createBlocksMenu(request));
    }

    public void processKeepInventoryToggle(Player player) {
        DuelRequest request = openConfig.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        request.setKeepInventory(!request.isKeepInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.1f, 1.2f);
        player.openInventory(createKeepInventoryMenu(request));
    }

    public void processArenaSelection(Player player, ItemStack item, int slot) {
        DuelRequest request = openConfig.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Arena selected
            ItemMeta meta = item.getItemMeta();
            String arenaName = meta.getPersistentDataContainer().get(arenaNameKey, PersistentDataType.STRING);
            if (arenaName == null) {
                arenaName = ChatColor.stripColor(meta.getDisplayName()).replace("✦", "").trim();
            }
            if (isArenaInUse(arenaName)) {
                player.sendMessage(configManager.format("§cThis arena is already in use. Please choose another one."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }
            Arena arena = arenaManager.getArena(arenaName);
            if (arena != null) {
                request.setArenaName(arenaName);
                request.setArenaCenter(arena.getCenter());
                player.sendMessage(configManager.getArenaSelected(arenaName));
                if (configManager.isSoundLevelUpEnabled()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, configManager.getLevelUpPitch());
                }
                player.openInventory(createKeepInventoryMenu(request));
            }
        }
    }

    public void toggleBlockOption(Player player, Material material) {
        DuelRequest request = openConfig.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        if (material == Material.ARROW) {
            request.setBlockArrows(!request.isBlockArrows());
        } else if (material == Material.FIREWORK_ROCKET) {
            request.setBlockFireworks(!request.isBlockFireworks());
        } else if (material == Material.POTION) {
            request.setBlockPotions(!request.isBlockPotions());
        } else if (material == Material.FEATHER) {
            request.setBlockSlowFalling(!request.isBlockSlowFalling());
        }
        if (configManager.isSoundButtonClickEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, configManager.getButtonClickPitch());
        }
        player.openInventory(createBlocksMenu(request));
    }

    public void sendDuelRequest(Player player) {
        DuelRequest request = openConfig.get(player.getUniqueId());
        if (request == null) {
            player.sendMessage(configManager.getErrorNoActiveSetup());
            return;
        }
        if (request.getArenaCenter() == null) {
            player.sendMessage(configManager.getErrorArenaNotSet());
            return;
        }
        if (request.getArenaName() != null && isArenaInUse(request.getArenaName())) {
            player.sendMessage(configManager.format("§cThat arena is already in use. Please select a different arena."));
            return;
        }
        openConfig.remove(player.getUniqueId());
        pendingRequests.put(request.getId(), request);
        Player target = Bukkit.getPlayer(request.getTarget());
        if (target == null || !target.isOnline()) {
            player.sendMessage(configManager.getErrorPlayerNotFound());
            pendingRequests.remove(request.getId());
            return;
        }
        player.sendMessage(configManager.getDuelRequestSent(target.getName()));
        if (configManager.isSoundArrowHitEnabled()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, configManager.getArrowHitPitch());
        }

        TextComponent header = new TextComponent(configManager.getRawMessage("duel_request_header") + "\n");
        TextComponent body = new TextComponent(configManager.getDuelRequestBody(player.getName()) + "\n");
        TextComponent allowed = new TextComponent(configManager.getDuelAllowed(request.buildAllowedString()) + "\n");

        TextComponent accept = new TextComponent(configManager.getRawMessage("duel_accept") + " ");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + request.getId()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(configManager.getRawMessage("duel_accept_hover")).create()));

        TextComponent deny = new TextComponent(configManager.getRawMessage("duel_deny"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel deny " + request.getId()));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(configManager.getRawMessage("duel_deny_hover")).create()));

        target.spigot().sendMessage(header, body, allowed, accept, deny);
        target.sendMessage(configManager.getDuelArenaLocation(request.getArenaCenter().getBlockX(), request.getArenaCenter().getBlockY(), request.getArenaCenter().getBlockZ()));
        if (configManager.isSoundPickupEnabled()) {
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, configManager.getPickupSoundPitch());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRequests.remove(request.getId()) != null) {
                    if (player.isOnline()) {
                        player.sendMessage(configManager.getDuelRequestExpired(target.getName()));
                    }
                    if (target.isOnline()) {
                        target.sendMessage(configManager.getDuelRequestExpired(player.getName()));
                    }
                }
            }
        }.runTaskLater(plugin, configManager.getRequestExpiration());
    }

    private void startDuel(DuelRequest request, Player challenger, Player accepter) {
        if (activeSessions.containsKey(challenger.getUniqueId()) || activeSessions.containsKey(accepter.getUniqueId())) {
            accepter.sendMessage(configManager.format(configManager.getErrorAlreadyInDuel()));
            return;
        }
        DuelSession session = new DuelSession(request, challenger, accepter, this);
        session.start();
        activeSessions.put(challenger.getUniqueId(), session);
        activeSessions.put(accepter.getUniqueId(), session);
    }

    public DuelSession getSession(Player player) {
        DuelSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.isEnded()) {
            return null;
        }
        return session;
    }

    public DuelSession getAnySession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public boolean isArenaInUse(String arenaName) {
        if (arenaName == null || arenaName.isBlank()) {
            return false;
        }
        boolean duelInUse = activeSessions.values().stream()
                .anyMatch(session -> session != null && !session.isEnded() && arenaName.equalsIgnoreCase(session.getRequest().getArenaName()));
        if (duelInUse) {
            return true;
        }
        return partyManager.getAllActiveSessions().stream()
                .anyMatch(session -> session != null && !session.isEnded() && arenaName.equalsIgnoreCase(session.getParty().getArenaName()));
    }

    public void removeSession(DuelSession session) {
        activeSessions.remove(session.getPlayerAId());
        activeSessions.remove(session.getPlayerBId());
    }

    private void ensureLeaderboardFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        if (!leaderboardFile.exists()) {
            try {
                leaderboardFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().warning("Could not create leaderboard file: " + e.getMessage());
            }
        }
    }

    private void loadLeaderboard() {
        if (leaderboardFile == null) {
            ensureLeaderboardFile();
        }
        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
        leaderboard.clear();
        for (String key : leaderboardConfig.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                leaderboard.put(playerId, leaderboardConfig.getInt(key, 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveLeaderboard() {
        if (leaderboardFile == null) {
            ensureLeaderboardFile();
        }
        for (Map.Entry<UUID, Integer> entry : leaderboard.entrySet()) {
            leaderboardConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save leaderboard: " + e.getMessage());
        }
    }

    public void recordWin(Player winner) {
        if (winner == null) {
            return;
        }
        leaderboard.merge(winner.getUniqueId(), 1, Integer::sum);
        saveLeaderboard();
    }

    public int getWins(Player player) {
        if (player == null) {
            return 0;
        }
        return leaderboard.getOrDefault(player.getUniqueId(), 0);
    }

    private String getPlayerName(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
        return offlineName != null ? offlineName : playerId.toString();
    }

    public List<Map.Entry<UUID, Integer>> getTopLeaderboardEntries(int count) {
        return leaderboard.entrySet().stream()
                .sorted((a, b) -> {
                    int compare = b.getValue().compareTo(a.getValue());
                    if (compare == 0) {
                        return getPlayerName(a.getKey()).compareToIgnoreCase(getPlayerName(b.getKey()));
                    }
                    return compare;
                })
                .limit(count)
                .toList();
    }

    public String getLeaderboardDisplay() {
        List<Map.Entry<UUID, Integer>> top = getTopLeaderboardEntries(10);
        if (top.isEmpty()) {
            return configManager.format("§cNo leaderboard data yet.");
        }
        StringBuilder display = new StringBuilder("§6§lDuel Leaderboard\n");
        for (int i = 0; i < 10; i++) {
            if (i >= top.size()) {
                display.append("§7#").append(i + 1).append(" §8- §7No data\n");
                continue;
            }
            Map.Entry<UUID, Integer> entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            String name = player != null ? player.getName() : entry.getKey().toString();
            display.append("§7#").append(i + 1).append(" §e").append(name).append(" §7- §f").append(entry.getValue()).append(" wins\n");
        }
        return display.toString().trim();
    }

    public String getLeaderboardPlaceholder(String placeholder) {
        if (placeholder == null || !placeholder.startsWith("%duel_top")) {
            return "";
        }
        Pattern pattern = Pattern.compile("%duel_top(\\d+)_(player_name|wins)%");
        Matcher matcher = pattern.matcher(placeholder);
        if (!matcher.matches()) {
            return "";
        }
        int rank;
        try {
            rank = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return "";
        }
        String key = matcher.group(2);
        List<Map.Entry<UUID, Integer>> top = getTopLeaderboardEntries(rank);
        if (rank <= 0 || rank > top.size()) {
            return key.equals("player_name") ? "N/A" : "0";
        }
        Map.Entry<UUID, Integer> entry = top.get(rank - 1);
        if (key.equals("player_name")) {
            return getPlayerName(entry.getKey());
        }
        return entry.getValue().toString();
    }

    public String replaceLeaderboardPlaceholders(String text) {
        if (text == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("%duel_top(\\d+)_(player_name|wins)%");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(getLeaderboardPlaceholder(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public void promptRatingIfNeeded(Player player) {
        if (player == null || !player.isOnline() || playerRatings.containsKey(player.getUniqueId())) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.openInventory(createRatingMenu(player));
                }
            }
        }.runTaskLater(plugin, 200);
    }

    private Inventory createRatingMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, "§6Rate the Duel");

        ItemStack bad = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta badMeta = bad.getItemMeta();
        badMeta.setDisplayName("§cBad");
        badMeta.setLore(List.of("§7I don't like the duels yet.", "§71 point"));
        bad.setItemMeta(badMeta);

        ItemStack okay = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta okayMeta = okay.getItemMeta();
        okayMeta.setDisplayName("§eOkay");
        okayMeta.setLore(List.of("§7The duels are okay.", "§72 points"));
        okay.setItemMeta(okayMeta);

        ItemStack love = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta loveMeta = love.getItemMeta();
        loveMeta.setDisplayName("§bLove it");
        loveMeta.setLore(List.of("§7I love the duels!", "§73 points"));
        love.setItemMeta(loveMeta);

        inventory.setItem(3, bad);
        inventory.setItem(4, okay);
        inventory.setItem(5, love);

        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createFillerPane());
            }
        }
        return inventory;
    }

    public void processRatingClick(Player player, int slot) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int rating;
        if (slot == 3) {
            rating = 1;
        } else if (slot == 4) {
            rating = 2;
        } else if (slot == 5) {
            rating = 3;
        } else {
            return;
        }
        playerRatings.put(player.getUniqueId(), rating);
        player.sendMessage(configManager.format("§aThanks for rating the duel!"));
        player.closeInventory();
    }

    public String getRatingSummary() {
        if (playerRatings.isEmpty()) {
            return configManager.format("§cNo duel ratings have been collected yet.");
        }
        int total = playerRatings.values().stream().mapToInt(Integer::intValue).sum();
        int count = playerRatings.size();
        double average = (double) total / count;
        int bad = (int) playerRatings.values().stream().filter(r -> r == 1).count();
        int okay = (int) playerRatings.values().stream().filter(r -> r == 2).count();
        int love = (int) playerRatings.values().stream().filter(r -> r == 3).count();
        return configManager.format("§6Duel Ratings: §7Average: §e" + String.format("%.2f", average) + " §7(§e" + count + "§7 votes) §7- §cBad: §e" + bad + " §7| §eOkay: §e" + okay + " §7| §bLove: §e" + love);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        DuelSession session = getSession(player);
        if (session != null) {
            if (session.isCountdownActive()) {
                if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock()) && !session.isInArena(event.getTo())) {
                event.setCancelled(true);
            }
            return;
        }

        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession != null) {
            if (partySession.isCountdownActive()) {
                if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock()) && !partySession.isInArena(event.getTo())) {
                event.setCancelled(true);
                if (player.getGameMode() == GameMode.SPECTATOR && event.getFrom() != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(event.getFrom()), 1L);
                }
            }
            return;
        }

        // Prevent spectators from leaving the arena/worldborder during a team duel
        try {
            if (player.getGameMode() == GameMode.SPECTATOR && partySession != null) {
                if (event.getTo() != null && !partySession.isInArena(event.getTo())) {
                    event.setCancelled(true);
                    // teleport back to previous location (inside arena) to avoid leaving bounds
                    if (event.getFrom() != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(event.getFrom()), 1L);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        DuelSession session = getSession(player);
        // Extra check: block ender-pearl teleports outside the arena to prevent glitch-through
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (session != null && event.getTo() != null && !session.isInArena(event.getTo())) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cEnder pearl blocked: cannot teleport outside the duel arena."));
                return;
            }
            TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
            if (partySession != null && event.getTo() != null && !partySession.isInArena(event.getTo())) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cEnder pearl blocked: cannot teleport outside the arena while the duel is active."));
                return;
            }
        }

        if (session != null && event.getTo() != null && !session.isInArena(event.getTo())) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cYou cannot teleport outside the duel arena."));
            return;
        }
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession != null && event.getTo() != null && !partySession.isInArena(event.getTo())) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cYou cannot teleport outside the arena while the duel is active."));
        }
    }

    private Inventory createArenaSelectionMenu(DuelRequest request) {
        Inventory inventory = Bukkit.createInventory(null, 18, configManager.getMenuArenaSelection());
        Collection<String> arenaList = arenaManager.listArenas();
        int slot = 0;
        for (String arenaName : arenaList) {
            if (slot >= 18) break;
            Arena arena = arenaManager.getArena(arenaName);
                    if (arena != null && arena.getCenter() != null) {
                    boolean inUse = isArenaInUse(arenaName);
                    Material icon = inUse ? Material.BARRIER : arena.hasIconMaterial() ? arena.getIconMaterial() : Material.GRASS_BLOCK;
                    ItemStack item = new ItemStack(icon);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§6✦ §f" + arenaName + (inUse ? " §c[IN USE]" : " §a[AVAILABLE]"));
                    Location loc = arena.getCenter();
                    List<String> lore = new ArrayList<>();
                    lore.add("§7World: §f" + arena.getWorldName());
                    lore.add("§7Coordinates: §f" + loc.getBlockX() + " §7/ §f" + loc.getBlockY() + " §7/ §f" + loc.getBlockZ());
                    if (inUse) {
                        lore.add("§c❌ This arena is currently in use.");
                        lore.add("§eChoose another arena to avoid conflicts.");
                    } else {
                        lore.add("§a✅ Ready for duel");
                        lore.add("§eClick to select this arena");
                    }
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(arenaNameKey, PersistentDataType.STRING, arenaName);
                    meta.setEnchantmentGlintOverride(true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                    slot++;
                }
        }

        for (int i = 0; i < 18; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createFillerPane());
            }
        }

        return inventory;
    }

    private Inventory createKeepInventoryMenu(DuelRequest request) {
        Inventory inventory = Bukkit.createInventory(null, 18, configManager.getMenuKeepInventory());

        // Slot 0: Clock - Back to arena selection
        ItemStack clock = createPremiumItem(Material.CLOCK, "§6✦ Back to Arena Selection ✦", "§7Return to arena selection", "§8Choose a different arena");
        
        // Slot 4: Redstone - Keep Inventory Toggle
        ItemStack keepItem = new ItemStack(request.isKeepInventory() ? Material.EMERALD : Material.REDSTONE);
        ItemMeta keepMeta = keepItem.getItemMeta();
        keepMeta.setDisplayName("§eKeep Inventory: " + (request.isKeepInventory() ? "§aON" : "§cOFF"));
        keepMeta.setLore(List.of("§7Toggle whether player inventory", "§7is restored after the duel.", "§8Click to toggle"));
        keepItem.setItemMeta(keepMeta);

        // Slot 6: Iron Sword - Ready/Open Ready Menu
        ItemStack ready = createPremiumItem(Material.IRON_SWORD, "§b⚔ Ready Up", "§7Open the ready confirmation", "§8Both players must confirm");

        // Slot 8: Settings - Item Restrictions
        ItemStack settings = createPremiumItem(Material.ENCHANTED_BOOK, "§5⚙ Restrictions", "§7Configure which items to block", "§8Arrows, Fireworks, Potions, Slow Fall");

        // Slot 17: Barrier - Leave
        ItemStack leave = new ItemStack(Material.BARRIER);
        ItemMeta leaveMeta = leave.getItemMeta();
        leaveMeta.setDisplayName("§c✕ Leave");
        leaveMeta.setLore(List.of("§7Leave the duel setup", "§8You will exit the menu"));
        leave.setItemMeta(leaveMeta);

        inventory.setItem(0, clock);
        for (int i = 1; i < 4; i++) {
            inventory.setItem(i, createFillerPane());
        }
        inventory.setItem(4, keepItem);
        inventory.setItem(5, createFillerPane());
        inventory.setItem(6, ready);
        inventory.setItem(7, createFillerPane());
        inventory.setItem(8, settings);
        
        // Second row
        for (int i = 9; i < 17; i++) {
            inventory.setItem(i, createFillerPane());
        }
        inventory.setItem(17, leave);
        
        return inventory;
    }

    private Inventory createBlocksMenu(DuelRequest request) {
        Inventory inventory = Bukkit.createInventory(null, 9, configManager.getMenuRestrictions());

        inventory.setItem(0, createToggleItem(Material.ARROW, "§aBlock Arrows", request.isBlockArrows()));
        inventory.setItem(1, createToggleItem(Material.FIREWORK_ROCKET, "§aBlock Fireworks", request.isBlockFireworks()));
        inventory.setItem(2, createToggleItem(Material.POTION, "§aBlock Potions", request.isBlockPotions()));
        inventory.setItem(3, createToggleItem(Material.FEATHER, "§aBlock Slow Fall", request.isBlockSlowFalling()));

        inventory.setItem(4, createFillerPane());
        inventory.setItem(5, createFillerPane());
        inventory.setItem(6, createFillerPane());

        ItemStack back = createPremiumItem(Material.PAPER, "§bBack to Setup", "§7Return to the setup menu", "§8Change other settings.");

        inventory.setItem(7, back);
        inventory.setItem(8, createFillerPane());
        return inventory;
    }

    private ItemStack createToggleItem(Material material, String name, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + (enabled ? " §cON" : " §aOFF"));
        meta.setLore(List.of(enabled ? "§7This item is currently blocked." : "§7This item is currently allowed.", "§7Click to toggle."));
        if (enabled) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPremiumItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(List.of(lore));
        }
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private boolean isPartyCreateItem(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            ItemStack template = configManager.createPartyItem(key);
            if (template == null || !template.hasItemMeta()) return false;
            ItemMeta im = item.getItemMeta();
            ItemMeta tm = template.getItemMeta();
            String a = im.hasDisplayName() ? im.getDisplayName() : "";
            String b = tm.hasDisplayName() ? tm.getDisplayName() : "";
            return a.equals(b);
        } catch (Exception ignored) {
            return false;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Prevent moving configured party creation items in player inventory
        try {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                ItemStack curr = event.getCurrentItem();
                if (curr != null) {
                    if (isPartyCreateItem(curr, "main_item") || isPartyCreateItem(curr, "members_item") || isPartyCreateItem(curr, "restore_item")) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        DuelSession session = getSession(player);
        if (session != null) {
            // Allow all inventory movement during duel
            // Item restrictions are enforced at point of use (PlayerItemConsumeEvent, ProjectileLaunchEvent, etc.)
            return;
        }
        String title = event.getView().getTitle();
        if (!isPluginGui(title)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            // Allow interactions with the player's own inventory while GUI is open
            return;
        }
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);

        String normalizedTitle = normalizeTitle(title);
        if (normalizedTitle.contains("Arena Selection")) {
            if (event.getCurrentItem() == null) return;
            processArenaSelection(player, event.getCurrentItem(), event.getSlot());
        } else if (normalizedTitle.contains("Duel Setup - Keep Inventory")) {
            if (event.getCurrentItem() == null) return;
            int slot = event.getSlot();
            switch (slot) {
                case 0 -> {
                    // Clock: Back to arena selection
                    DuelRequest request = openConfig.get(player.getUniqueId());
                    if (request != null) {
                        player.openInventory(createArenaSelectionMenu(request));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                    }
                }
                case 4 -> {
                    // Redstone: Toggle keep inventory
                    processKeepInventoryToggle(player);
                }
                case 6 -> {
                    // Iron sword: Send duel request
                    sendDuelRequest(player);
                }
                case 8 -> {
                    // Settings: Open restrictions menu
                    DuelRequest req = openConfig.get(player.getUniqueId());
                    if (req != null) {
                        player.openInventory(createBlocksMenu(req));
                    }
                }
                case 17 -> {
                    // Barrier: Leave
                    openConfig.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(configManager.format("§7You left the duel setup."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                }
            }
        } else if (normalizedTitle.contains("Duel Setup - Restrictions")) {
            if (event.getCurrentItem() == null) return;
            int slot = event.getSlot();
            if (slot >= 0 && slot <= 3) {
                toggleBlockOption(player, event.getCurrentItem().getType());
            } else if (slot == 7) {
                DuelRequest request = openConfig.get(player.getUniqueId());
                if (request != null) {
                    player.openInventory(createKeepInventoryMenu(request));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                }
            }
        } else if (normalizedTitle.contains("Rate the Duel")) {
            if (event.getCurrentItem() == null) return;
            processRatingClick(player, event.getSlot());
        } else if ("Party Menu".equals(normalizedTitle)) {
            partyManager.handlePartyMenuClick(player, event.getSlot());
        } else if ("Party Settings".equals(normalizedTitle)) {
            partyManager.handleSettingsMenuClick(player, event.getSlot());
        } else if ("Party Teams".equals(normalizedTitle)) {
            partyManager.handleTeamMenuClick(player, event.getSlot());
        } else if ("Party Selection".equals(normalizedTitle)) {
            // New Party Menu GUI - 1v1 vs Teams selection
            handleNewPartyMenuClick(player, event.getSlot());
        } else if ("Select Party Member".equals(normalizedTitle) || "Party Member Selection".equals(normalizedTitle)) {
            handlePartyMemberSelectionClick(player, event.getSlot());
        } else if ("Team Members - Max 14".equals(normalizedTitle)) {
            // Team Invite GUI
            handleTeamInviteClick(player, event.getSlot());
        } else if ("Team Selection".equals(normalizedTitle)) {
            // Team Selection GUI
            handleTeamSelectionClick(player, event.getSlot());
        } else if ("Duel Options".equals(normalizedTitle)) {
            // Duel Options GUI
            handleDuelOptionsClick(player, event.getSlot());
        } else if ("Keep Inventory Setting".equals(normalizedTitle)) {
            // Keep Inventory GUI
            handleKeepInventoryClick(player, event.getSlot());
        } else if ("Team Duel Settings".equals(normalizedTitle)) {
            // Settings GUI
            handleSettingsGuiClick(player, event.getSlot());
        } else if ("Ready".equals(normalizedTitle)) {
            handleReadyGuiClick(player, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        DuelSession session = getSession(player);
        if (session != null) {
            // Allow all dragging during duel
            // Item restrictions are enforced at point of use
            return;
        }
        String title = event.getView().getTitle();
        if (!isPluginGui(title)) {
            return;
        }
        // Prevent all dragging in plugin GUIs - only allow clicks
        event.setCancelled(true);
    }

    private boolean isPluginGui(String title) {
        if (title == null) {
            return false;
        }
        String normalizedTitle = normalizeTitle(title);
        return normalizedTitle.contains("Arena Selection")
                || normalizedTitle.contains("Duel Setup")
                || normalizedTitle.contains("Rate the Duel")
                || normalizedTitle.contains("Party Menu")
                || normalizedTitle.contains("Party Selection")
                || normalizedTitle.contains("Select Party Member")
                || normalizedTitle.contains("Party Member Selection")
                || normalizedTitle.contains("Party Settings")
                || normalizedTitle.contains("Party Teams")
                || normalizedTitle.contains("Team Members")
                || normalizedTitle.contains("Team Selection")
                || normalizedTitle.contains("Keep Inventory")
                || normalizedTitle.contains("Team Duel Settings")
                || normalizedTitle.contains("Duel Options")
                || normalizedTitle.contains("Ready");
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return ChatColor.stripColor(title).replace("§", "").trim();
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isPluginGui(player.getOpenInventory().getTitle())) {
            event.setCancelled(true);
        }
        // Prevent dropping configured party creation items
        try {
            ItemStack dropped = event.getItemDrop() != null ? event.getItemDrop().getItemStack() : null;
            if (dropped != null && (isPartyCreateItem(dropped, "main_item") || isPartyCreateItem(dropped, "members_item") || isPartyCreateItem(dropped, "restore_item"))) {
                event.setCancelled(true);
                return;
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isPluginGui(player.getOpenInventory().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // No special handling required when party settings or other plugin GUIs close.
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        TeamDuelSession session = partyManager.getActiveSession(sender.getUniqueId());
        if (session == null) {
            return;
        }
        Party party = session.getParty();
        event.getRecipients().clear();
        for (UUID member : party.getMembers()) {
            Player memberPlayer = Bukkit.getPlayer(member);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                event.getRecipients().add(memberPlayer);
            }
        }
        event.getRecipients().add(sender);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        TeamDuelSession session = partyManager.getActiveSession(attacker.getUniqueId());
        if (session == null || !session.getParty().isMember(target.getUniqueId())) {
            return;
        }
        if (session.getParty().getTeam(attacker.getUniqueId()).equalsIgnoreCase(session.getParty().getTeam(target.getUniqueId()))) {
            event.setCancelled(true);
            attacker.sendMessage(configManager.format("§cYou cannot attack teammates in this team duel."));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        DuelSession session = getSession(player);
        if (session != null && event.getItem() != null) {
            Material type = event.getItem().getType();
            if (session.isBlockedMaterial(type)) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cThat item is blocked in this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
            }
        }
        // Handle wand corner selection
        // Handle party creation items (open menus / restore)
        try {
            if (event.getItem() != null && event.getItem().hasItemMeta()) {
                ItemStack it = event.getItem();
                if (event.getAction().toString().contains("RIGHT")) {
                    if (isPartyCreateItem(it, "main_item")) {
                        partyManager.openPartyMenu(player);
                        player.playSound(player.getLocation(), Sound.valueOf(configManager.getPartyCreateSound("open_menu", "ENTITY_PLAYER_LEVELUP")), 1.0f, 1.0f);
                        event.setCancelled(true);
                        return;
                    }
                    if (isPartyCreateItem(it, "members_item")) {
                        partyManager.openTeamMenu(player);
                        player.playSound(player.getLocation(), Sound.valueOf(configManager.getPartyCreateSound("open_members", "BLOCK_CHEST_OPEN")), 1.0f, 1.0f);
                        event.setCancelled(true);
                        return;
                    }
                    if (isPartyCreateItem(it, "restore_item")) {
                        partyManager.restorePartyCreationInventory(player);
                        partyManager.disbandParty(player);
                        player.playSound(player.getLocation(), Sound.valueOf(configManager.getPartyCreateSound("restore_inventory", "BLOCK_ANVIL_USE")), 1.0f, 1.0f);
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (event.getItem() != null && event.getItem().getType() == Material.BLAZE_ROD && event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            String arenaName = null;
            // Prefer persistent data container value if available
            JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ");
            if (plugin != null) {
                NamespacedKey key = new NamespacedKey(plugin, "duels_arena");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    arenaName = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                }
            }
            if (arenaName == null && meta.hasDisplayName() && meta.getDisplayName().contains("Wand:")) {
                String display = meta.getDisplayName();
                String[] parts = display.split(":", 2);
                if (parts.length >= 2) {
                    arenaName = parts[1].trim();
                    arenaName = org.bukkit.ChatColor.stripColor(arenaName).trim();
                }
            }
            if (arenaName != null) {
                    if (!arenaManager.arenaExists(arenaName)) {
                        event.getPlayer().sendMessage(configManager.getArenaNotFound());
                        return;
                    }
                    Location targetLocation = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : event.getPlayer().getLocation();
                    // protect wand handling
                    JavaPlugin pluginInst = (JavaPlugin) Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ");
                    if (pluginInst != null) {
                        NamespacedKey protectKey = new NamespacedKey(pluginInst, "duels_protect");
                        if (meta.getPersistentDataContainer().has(protectKey, PersistentDataType.STRING)) {
                            String aName = meta.getPersistentDataContainer().get(protectKey, PersistentDataType.STRING);
                            if (aName != null && arenaManager.arenaExists(aName)) {
                                if (event.getAction().toString().contains("LEFT")) {
                                    arenaManager.addProtectedBlock(aName, targetLocation);
                                    event.getPlayer().sendMessage(configManager.format("§aProtected block added for arena §e" + aName));
                                    event.setCancelled(true);
                                    return;
                                } else if (event.getAction().toString().contains("RIGHT")) {
                                    arenaManager.removeProtectedBlock(aName, targetLocation);
                                    event.getPlayer().sendMessage(configManager.format("§aProtected block removed for arena §e" + aName));
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                        }
                    }
                    if (event.getAction().toString().contains("LEFT")) {
                        arenaManager.setCorner(arenaName, 1, targetLocation);
                        event.getPlayer().sendMessage(configManager.format("§a✔ Corner 1 set for arena §e" + arenaName));
                        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.2f, 1.0f);
                        event.getPlayer().spawnParticle(org.bukkit.Particle.CRIT, targetLocation.add(0.5, 1, 0.5), 40, 0.5, 0.5, 0.5, 0.1);
                        event.setCancelled(true);
                        return;
                    } else if (event.getAction().toString().contains("RIGHT")) {
                        arenaManager.setCorner(arenaName, 2, targetLocation);
                        event.getPlayer().sendMessage(configManager.format("§a✔ Corner 2 set for arena §e" + arenaName));
                        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.8f);
                        event.getPlayer().spawnParticle(org.bukkit.Particle.END_ROD, targetLocation.add(0.5, 1, 0.5), 40, 0.5, 0.5, 0.5, 0.1);
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        DuelSession session = getSession(player);
        if (session != null) {
            if (session.isBlockPotions() && isPotion(event.getItem())) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cPotions are blocked during this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
                return;
            }
            if (session.isBlockSlowFalling() && isSlowFallingItem(event.getItem())) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cSlow falling is blocked during this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
            }
            return;
        }
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession == null) {
            return;
        }
        Party party = partySession.getParty();
        if (party.isBlockPotions() && isPotion(event.getItem())) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cPotions are blocked during this duel."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
            return;
        }
        if (party.isBlockSlowFalling() && isSlowFallingItem(event.getItem())) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cSlow falling is blocked during this duel."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
        }
    }

    private boolean isPotion(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW;
    }

    private boolean isSlowFallingItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getItemMeta() instanceof PotionMeta meta) {
            return meta.getCustomEffects().stream().anyMatch(effect -> effect.getType() == PotionEffectType.SLOW_FALLING);
        }
        return false;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        // Determine shooter and enforce blocked projectile rules
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        Player player = shooter;
        DuelSession session = getSession(player);
        EntityType type = event.getEntity().getType();
        if (session != null) {
            if (session.isBlockArrows() && type == EntityType.ARROW) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cArrows are blocked during this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 0.6f);
            }
            if (session.isBlockFireworks() && type == EntityType.FIREWORK_ROCKET) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cFirework rockets are blocked during this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
            }
            if (session.isBlockPotions() && event.getEntity() instanceof ThrownPotion) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cThrown potions are blocked during this duel."));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
            }
            // prevent throwing ender pearls for a short cooldown after duel ends
            if (type == EntityType.ENDER_PEARL && session.isEnded()) {
                long until = session.getPearlBlockUntil();
                if (System.currentTimeMillis() < until) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                    player.sendMessage(configManager.format("§cEnder pearls are disabled for a short time after the duel. Please wait a few seconds."));
                    return;
                }
            }
            return;
        }
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession == null) {
            return;
        }
        Party party = partySession.getParty();
        if (party.isBlockArrows() && type == EntityType.ARROW) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cArrows are blocked during this duel."));
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 0.6f);
        }
        if (party.isBlockFireworks() && type == EntityType.FIREWORK_ROCKET) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cFirework rockets are blocked during this duel."));
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
        }
        if (party.isBlockPotions() && event.getEntity() instanceof ThrownPotion) {
            event.setCancelled(true);
            player.sendMessage(configManager.format("§cThrown potions are blocked during this duel."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.2f);
        }
        if (type == EntityType.ENDER_PEARL && partySession != null && partySession.isEnded()) {
            long until = partySession.getPearlBlockUntil();
            if (System.currentTimeMillis() < until) {
                event.setCancelled(true);
                event.getEntity().remove();
                player.sendMessage(configManager.format("§cEnder pearls are disabled for a short time after the duel. Please wait a few seconds."));
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession != null) {
            if (partySession.getParty().isKeepInventory()) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            event.setDeathMessage(null);
            partySession.handlePlayerDeath(player);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.spigot().respawn();
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                }
            }.runTaskLater(plugin, 1);
            return;
        }
        DuelSession session = getSession(player);
        if (session == null) {
            return;
        }
        Player winner = session.getOpponent(player);
        if (session.isKeepInventorySession()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        // Suppress default death message and send duel-specific message
        event.setDeathMessage(null);
        String killerName = "unknown";
        String weapon = "unknown";
        if (player.getKiller() != null) {
            killerName = player.getKiller().getName();
            ItemStack hand = player.getKiller().getInventory().getItemInMainHand();
            if (hand != null && hand.getType() != Material.AIR) {
                weapon = hand.getType().name();
            }
        }
        String duelDeath = "§c" + player.getName() + " got killed with " + weapon + " by " + killerName + " in a duel!";
        Bukkit.broadcastMessage(configManager.format(duelDeath));

        Location deathLoc = player.getLocation().clone();
        // Lightning effect without fire damage - only visual effect
        deathLoc.getWorld().strikeLightningEffect(deathLoc);
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, deathLoc.add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
        player.getWorld().playSound(deathLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.9f);

        session.finishDuel(winner, player, "Victory");

        // Ensure the player is immediately respawned to avoid death screen
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                try {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SPECTATOR);
                } catch (Throwable ignored) {
                    // best-effort fallback: restore health and teleport to arena center
                    if (player.isOnline()) {
                        player.setHealth(player.getMaxHealth());
                        player.setFoodLevel(20);
                        if (session.getArenaCenter() != null) {
                            player.teleport(session.getArenaCenter().clone().add(0,1,0));
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // persist inventory if player is in an active duel or party duel
        try {
            if (getAnySession(player) != null || partyManager.getActiveSession(player.getUniqueId()) != null) {
                saveInventoryBackup(player);
            }
        } catch (Exception ignored) {}
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (partySession != null) {
            partySession.handlePlayerQuit(player);
            return;
        }
        Party party = partyManager.getParty(player);
        if (party != null && party.isLeader(player.getUniqueId()) && partyManager.getActiveSession(player.getUniqueId()) == null) {
            partyManager.disbandParty(player);
        }
        DuelSession session = getSession(player);
        if (session != null) {
            Player winner = session.getOpponent(player);
            session.finishDuel(winner, player, "Opponent left");
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // attempt to restore any persisted duel backup first
        try { restoreInventoryBackup(player); } catch (Exception ignored) {}
        DuelSession session = getAnySession(player);
        if (session != null && session.isEnded() && session.isRestorePending()) {
            session.restoreForPlayer(player);
        }
        TeamDuelSession partySession = partyManager.getAnyActiveSession(player.getUniqueId());
        if (partySession != null && partySession.isEnded() && partySession.isRestorePending()) {
            partySession.restoreForPlayer(player);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        DuelSession session = getSession(player);
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (session == null && partySession == null) return;

        if (session != null) {
            if (session.isEnded()) {
                return;
            }
            if (session.isInArena(event.getBlock().getLocation())) {
                session.addPlacedBlock(event.getBlock().getLocation(), player.getUniqueId());
            }
            return;
        }

        if (partySession != null) {
            if (partySession.isEnded()) {
                return;
            }
            if (partySession.isInArena(event.getBlock().getLocation())) {
                partySession.addPlacedBlock(event.getBlock().getLocation(), player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        DuelSession session = getSession(player);
        TeamDuelSession partySession = partyManager.getActiveSession(player.getUniqueId());
        if (session == null && partySession == null) return;

        if (session != null) {
            if (session.isEnded()) {
                if (session.isInArena(event.getBlock().getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage(configManager.format("§cYou cannot break blocks in the arena after the duel has ended."));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                }
                return;
            }
            if (!session.isInArena(event.getBlock().getLocation())) {
                return;
            }
            if (!session.canBreakBlock(event.getBlock().getLocation(), player)) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cYou can only break blocks you placed during the duel."));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            } else {
                session.recordBlockChange(event.getBlock().getLocation(), event.getBlock().getBlockData());
                session.removePlacedBlock(event.getBlock().getLocation());
            }
            return;
        }

        if (partySession != null) {
            if (partySession.isEnded()) {
                if (partySession.isInArena(event.getBlock().getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage(configManager.format("§cYou cannot break blocks in the arena after the duel has ended."));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                }
                return;
            }
            if (!partySession.isInArena(event.getBlock().getLocation())) {
                return;
            }
            if (!partySession.canBreakBlock(event.getBlock().getLocation(), player)) {
                event.setCancelled(true);
                player.sendMessage(configManager.format("§cYou can only break blocks you placed during the duel."));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            } else {
                partySession.recordBlockChange(event.getBlock().getLocation(), event.getBlock().getBlockData());
                partySession.removePlacedBlock(event.getBlock().getLocation());
            }
        }
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public LuckPermsSupport getLuckPermsSupport() {
        return luckPermsSupport;
    }

    public void removePartySession(TeamDuelSession session) {
        partyManager.removePartySession(session);
    }

    private void handleNewPartyMenuClick(Player player, int slot) {
        Party party = partyManager.getParty(player);
        if (slot == 11) {
            if (party == null || !party.isLeader(player.getUniqueId())) {
                player.sendMessage(configManager.format("§cOnly the party leader can start a 1v1 challenge."));
                return;
            }
            player.closeInventory();
            dev.divsersmp.duels.gui.PartyMemberSelectionGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        } else if (slot == 15) {
            player.closeInventory();
            player.sendMessage(configManager.format("§9Team Duel mode selected!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            dev.divsersmp.duels.gui.TeamInviteGUI.open(player);
        }
    }

    private void handleTeamInviteClick(Player player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null) return;

        if (item.getType() == Material.BARRIER) {
            // Back button
            player.closeInventory();
            dev.divsersmp.duels.gui.PartyMenuGUI.open(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
            return;
        }

        if (item.getType() == Material.IRON_SWORD) {
            // Next button
            player.closeInventory();
            dev.divsersmp.duels.gui.TeamSelectionGUI.open(player, partyManager.getParty(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (item.getType() == Material.PLAYER_HEAD) {
            // Player head - send invite
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                if (target != null && target.isOnline()) {
                    partyManager.invitePlayer(player, target);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                } else {
                    player.sendMessage(configManager.format("§cPlayer is not online!"));
                }
            }
        }
    }

    private void handleTeamSelectionClick(Player player, int slot) {
        Party party = partyManager.getParty(player);
        if (slot == 9) {
            player.closeInventory();
            dev.divsersmp.duels.gui.TeamInviteGUI.open(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
            return;
        }
        if (slot == 17) {
            player.closeInventory();
            dev.divsersmp.duels.gui.KeepInventoryGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            return;
        }

        // Randomize teams horn
        if (slot == 13) {
            if (party == null || !party.isLeader(player.getUniqueId())) {
                player.sendMessage(configManager.format("§cOnly the party leader can randomize teams."));
                return;
            }
            java.util.List<java.util.UUID> members = new java.util.ArrayList<>(party.getMembers());
            java.util.Collections.shuffle(members);
            int half = members.size() / 2;
            for (int i = 0; i < members.size(); i++) {
                party.setTeam(members.get(i), i < half ? "lime" : "red");
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            dev.divsersmp.duels.gui.TeamSelectionGUI.open(player, party);
            return;
        }

        // Clicking player heads in top row (0-8 red) or bottom row (18-26 lime)
        if ((slot >= 0 && slot <= 8) || (slot >= 18 && slot <= 26)) {
            ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
            if (item == null || item.getType() != Material.PLAYER_HEAD) return;
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
            if (sm == null || sm.getOwningPlayer() == null) return;
            Player target = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
            if (target == null || !target.isOnline()) return;
            if (party == null || !party.isLeader(player.getUniqueId())) {
                player.sendMessage(configManager.format("§cOnly the party leader can assign teams."));
                return;
            }
            String current = party.getTeam(target.getUniqueId());
            String nextTeam = "lime".equalsIgnoreCase(current) ? "red" : "lime";
            party.setTeam(target.getUniqueId(), nextTeam);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            dev.divsersmp.duels.gui.TeamSelectionGUI.open(player, party);
        }
    }

    private void handleKeepInventoryClick(Player player, int slot) {
        Party party = partyManager.getParty(player);
        if (party == null) {
            return;
        }
        if (slot == 10) {
            party.setKeepInventory(false);
            player.closeInventory();
            dev.divsersmp.duels.gui.DuelOptionsGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 1.0f);
        } else if (slot == 16) {
            party.setKeepInventory(true);
            player.closeInventory();
            dev.divsersmp.duels.gui.DuelOptionsGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);
        } else if (slot == 18) {
            player.closeInventory();
            dev.divsersmp.duels.gui.TeamSelectionGUI.open(player, partyManager.getParty(player));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
        }
    }

    private void handlePartyMemberSelectionClick(Player player, int slot) {
        if (slot == 0) {
            player.closeInventory();
            dev.divsersmp.duels.gui.PartyMenuGUI.open(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
            return;
        }
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }
        SkullMeta sm = (SkullMeta) item.getItemMeta();
        if (sm == null || sm.getOwningPlayer() == null) {
            return;
        }
        Player target = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
        if (target == null || !target.isOnline()) {
            player.sendMessage(configManager.format("§cThat player is not online."));
            return;
        }
        Party party = partyManager.getParty(player);
        if (party == null || !party.isLeader(player.getUniqueId())) {
            player.sendMessage(configManager.format("§cOnly the party leader can select a duel opponent."));
            return;
        }
        if (!party.isMember(target.getUniqueId())) {
            player.sendMessage(configManager.format("§cThat player is not part of your party."));
            return;
        }
        player.closeInventory();
        openConfigurationMenu(player, target);
    }

    private void handleDuelOptionsClick(Player player, int slot) {
        Party party = partyManager.getParty(player);
        if (party == null) return;
        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage(configManager.format("§cOnly the party leader can change these options."));
            return;
        }
        switch (slot) {
            case 11 -> {
                party.setBlockFireworks(!party.isBlockFireworks());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 12 -> {
                party.setBlockPotions(!party.isBlockPotions());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 13 -> {
                party.setBlockArrows(!party.isBlockArrows());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 14 -> {
                party.setBlockSlowFalling(!party.isBlockSlowFalling());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 15 -> {
                party.setBlockMace(!party.isBlockMace());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 9 -> {
                // Back to Keep Inventory GUI
                dev.divsersmp.duels.gui.KeepInventoryGUI.open(player, party);
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
                return;
            }
            case 17 -> {
                player.closeInventory();
                dev.divsersmp.duels.gui.ModernReadyGUI.open(player, party);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                return;
            }
            default -> {
                return;
            }
        }
        // reopen to reflect toggles
        dev.divsersmp.duels.gui.DuelOptionsGUI.open(player, party);
    }

    private void handleSettingsGuiClick(Player player, int slot) {
        if (slot == 18) {
            Party party = partyManager.getParty(player);
            if (party == null) return;
            player.closeInventory();
            dev.divsersmp.duels.gui.KeepInventoryGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
        } else if (slot == 26) {
            Party party = partyManager.getParty(player);
            if (party == null) {
                return;
            }
            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage(configManager.format("§cOnly the party leader can start the duel."));
                return;
            }
            player.closeInventory();
            dev.divsersmp.duels.gui.ModernReadyGUI.open(player, party);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    private void handleReadyGuiClick(Player player, int slot) {
        Party party = partyManager.getParty(player);
        if (party == null) {
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            if (party.isReady(player.getUniqueId())) {
                player.closeInventory();
                player.sendMessage(configManager.format("§eYou are already queued for the duel."));
                return;
            }
            party.setReady(player.getUniqueId(), true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            if (party.areAllReady()) {
                player.closeInventory();
                if (!partyManager.startPartyDuel(party)) {
                    player.sendMessage(configManager.format("§cUnable to start the duel. Make sure your party has at least 2 players and an arena is selected."));
                } else {
                    party.clearReady();
                }
            } else {
                // Open a status GUI showing who is ready. Players may close this GUI but remain queued.
                dev.divsersmp.duels.gui.ReadyStatusGUI.open(player, party);
                player.sendMessage(configManager.format("§aYou are queued for the duel."));
                if (party.isLeader(player.getUniqueId())) {
                    for (UUID memberId : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            Bukkit.getScheduler().runTask(plugin, () -> dev.divsersmp.duels.gui.ReadyStatusGUI.open(member, party));
                        }
                    }
                }
            }
        } else if (slot == 19) {
            player.closeInventory();
            player.sendMessage(configManager.format("§7Ready menu closed."));
        }
    }
}

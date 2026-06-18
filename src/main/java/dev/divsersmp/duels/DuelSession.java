package dev.divsersmp.duels;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DuelSession {
    private final DuelRequest request;
    private final Player playerA;
    private final Player playerB;
    private final DuelManager manager;
    private final ConfigManager configManager;
    private final JavaPlugin plugin;
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private final Map<Location, UUID> playerPlaced = new HashMap<>();
    private final Set<Location> barrierLocations = new HashSet<>();
    private final Map<UUID, PlayerInventorySnapshot> backups = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private long startedAt = System.currentTimeMillis();
    private boolean ended = false;
    private boolean countdownActive = true;
    private boolean awaitingReturn = false;
    private boolean restorePending = false;
    private boolean worldBorderApplied = false;
    private double previousBorderSize = -1;
    private double previousBorderCenterX;
    private double previousBorderCenterZ;
    private long pearlBlockUntil = 0L;
    private final java.util.Set<java.util.UUID> dropping = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, org.bukkit.Location> dropOrigin = new java.util.HashMap<>();
    private static final String PREFIX = "§7[§bDivserDuels§7] §r";

    public DuelSession(DuelRequest request, Player playerA, Player playerB, DuelManager manager) {
        this.request = request;
        this.playerA = playerA;
        this.playerB = playerB;
        this.manager = manager;
        this.configManager = manager.getConfigManager();
        this.plugin = manager.getPlugin();
    }

    public void start() {
        this.returnLocations.put(playerA.getUniqueId(), playerA.getLocation().clone());
        this.returnLocations.put(playerB.getUniqueId(), playerB.getLocation().clone());
        this.countdownActive = true;

        backups.put(playerA.getUniqueId(), new PlayerInventorySnapshot(playerA));
        backups.put(playerB.getUniqueId(), new PlayerInventorySnapshot(playerB));

        Arena arena = manager.getArenaManager().getArena(request.getArenaName());
        if (arena != null && arena.hasCorners()) {
            manager.getArenaManager().saveSnapshot(arena);
        }
        captureArenaSnapshot(arena);

        // Register protected blocks for this arena so they are restored after duel
        try {
            if (arena != null) {
                for (Location pLoc : manager.getArenaManager().getProtectedBlocksForArena(arena.getName())) {
                    org.bukkit.block.data.BlockData original = manager.getArenaManager().getProtectedOriginal(arena.getName(), pLoc);
                    if (original != null) {
                        recordBlockChange(pLoc, original);
                        barrierLocations.add(new Location(pLoc.getWorld(), pLoc.getBlockX(), pLoc.getBlockY(), pLoc.getBlockZ()));
                    }
                }
            }
        } catch (Exception ignored) {}

        Location center = request.getArenaCenter();
        
        // Use custom spawns if available
        Location spawn1, spawn2;
        if (arena != null && arena.hasSpawns()) {
            spawn1 = arena.getSpawn1();
            spawn2 = arena.getSpawn2();
            if (spawn1 == null || spawn2 == null) {
                spawn1 = center.clone().add(3, 1, 0);
                spawn2 = center.clone().add(-3, 1, 0);
            }
        } else {
            spawn1 = center.clone().add(3, 1, 0);
            spawn2 = center.clone().add(-3, 1, 0);
        }

        // Raise spawn 5 blocks for slow falling effect
        spawn1 = spawn1.clone().add(0, 5, 0);
        spawn2 = spawn2.clone().add(0, 5, 0);

        // Teleport players to their spawns
        playerA.teleport(spawn1);
        playerB.teleport(spawn2);
        
        // Play initial sounds and effects
        playerA.playSound(playerA.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 1.2f);
        playerB.playSound(playerB.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 1.2f);
        
        // Start countdown sequence
        startCountdown(center);
    }

    private void startCountdown(Location center) {
        final int[] countdown = { manager.getConfigManager().getDropSeconds() };

        new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    String countdownText = countdown[0] == 1 ? "§c1" : "§e" + countdown[0];
                    String subtitle = countdown[0] == 1 ? "§7Fight starts now — do not move!" : "§7Stand still and prepare for battle";
                    playerA.sendTitle(countdownText, subtitle, 2, 12, 6);
                    playerB.sendTitle(countdownText, subtitle, 2, 12, 6);

                    if (countdown[0] == 5) {
                        playerA.sendMessage(PREFIX + "§6The duel begins in 5 seconds. Do not move!");
                        playerB.sendMessage(PREFIX + "§6The duel begins in 5 seconds. Do not move!");
                    }

                    Sound sound = countdown[0] == 1 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_WOODEN_BUTTON_CLICK_ON;
                    float pitch = countdown[0] == 1 ? 1.5f : 1.0f;
                    playerA.playSound(playerA.getLocation(), sound, 1.0f, pitch);
                    playerB.playSound(playerB.getLocation(), sound, 1.0f, pitch);

                    for (int i = 0; i < 8; i++) {
                        playerA.spawnParticle(Particle.CRIT, playerA.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0.1);
                        playerB.spawnParticle(Particle.CRIT, playerB.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0.1);
                    }

                    countdown[0]--;
                } else {
                    startDuel(center);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startDuel(Location center) {
        this.countdownActive = false;
        this.startedAt = System.currentTimeMillis();
        // Play dramatic sounds
        playerA.playSound(playerA.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        playerB.playSound(playerB.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        playerA.playSound(playerA.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        playerB.playSound(playerB.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        
        // Spawn particle effects
        Location centerClone = center.clone().add(0, 1, 0);
        playerA.spawnParticle(Particle.FLAME, centerClone, 60, 2, 1, 2, 0.2);
        playerB.spawnParticle(Particle.CRIT, centerClone, 60, 2, 1, 2, 0.2);
        playerA.spawnParticle(Particle.EXPLOSION, playerA.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.1);
        playerB.spawnParticle(Particle.EXPLOSION, playerB.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.1);
        
        // Custom arena boundary enforcement is handled via session checks, not Bukkit world borders.
        Arena arena = manager.getArenaManager().getArena(request.getArenaName());
        if (arena != null) {
            try {
                dev.divsersmp.duels.ArenaManager.WorldBorderState prev = manager.getArenaManager().applyWorldBorder(arena, playerA, playerB);
                if (prev != null) {
                    worldBorderApplied = true;
                    previousBorderCenterX = prev.getCenterX();
                    previousBorderCenterZ = prev.getCenterZ();
                    previousBorderSize = prev.getSize();
                }
            } catch (Exception ignored) {}
        }

        // Apply duel scoreboard
        try {
            manager.getScoreboardService().applyDuelScoreboard(this);
        } catch (Exception ignored) {}
        // Play a short drop animation: drop 10 blocks slowly with Slow Falling
        try {
            int dropTicks = manager.getConfigManager().getDropSeconds() * 20;
            startDropAnimation(playerA, 10, dropTicks);
            startDropAnimation(playerB, 10, dropTicks);
        } catch (Exception ignored) {}
        
        // Chat messages
        playerA.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
        playerA.sendMessage(PREFIX + "§a⚔ Duel Started! ⚔");
        playerA.sendMessage(PREFIX + "§7Opponent: §e" + playerB.getName());
        playerA.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
        playerB.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
        playerB.sendMessage(PREFIX + "§a⚔ Duel Started! ⚔");
        playerB.sendMessage(PREFIX + "§7Opponent: §e" + playerA.getName());
        playerB.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public Player getPlayerA() {
        return playerA;
    }

    public Player getPlayerB() {
        return playerB;
    }

    public UUID getPlayerAId() {
        return playerA.getUniqueId();
    }

    public UUID getPlayerBId() {
        return playerB.getUniqueId();
    }

    public Location getArenaCenter() {
        return request.getArenaCenter();
    }

    public Player getOpponent(Player player) {
        return player.getUniqueId().equals(playerA.getUniqueId()) ? playerB : playerA;
    }

    public boolean isDropping(java.util.UUID playerId) {
        return dropping.contains(playerId);
    }

    private void startDropAnimation(org.bukkit.entity.Player player, int blocks, int totalTicks) {
        if (player == null || !player.isOnline()) return;
        java.util.UUID id = player.getUniqueId();
        if (dropping.contains(id)) return;
        dropping.add(id);
        dropOrigin.put(id, player.getLocation().clone());
        // Apply Slow Falling
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, totalTicks + 20, 0, true, false, true));

        final double total = blocks;
        final int steps = Math.max(1, totalTicks);
        final double delta = total / steps;

        new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || tick >= steps) {
                    dropping.remove(id);
                    dropOrigin.remove(id);
                    this.cancel();
                    return;
                }
                try {
                    org.bukkit.Location loc = player.getLocation().clone();
                    loc.setY(loc.getY() - delta);
                    // keep X/Z fixed to origin to prevent horizontal drift
                    org.bukkit.Location origin = dropOrigin.get(id);
                    if (origin != null) {
                        loc.setX(origin.getX());
                        loc.setZ(origin.getZ());
                    }
                    player.teleport(loc);
                } catch (Exception ignored) {}
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public boolean isKeepInventorySession() {
        return request.isKeepInventory();
    }

    public DuelRequest getRequest() {
        return request;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public DuelManager getManager() {
        return manager;
    }

    public boolean isInArena(Location location) {
        if (location == null) {
            return false;
        }
        Arena arena = manager.getArenaManager().getArena(request.getArenaName());
        return arena != null && manager.getArenaManager().isLocationInsideArena(arena, location);
    }

    public boolean isPlayerPlaced(Location loc) {
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return playerPlaced.containsKey(key);
    }

    public boolean canBreakBlock(Location loc, Player player) {
        if (ended) {
            return false;
        }
        if (player == null) return false;
        if (!isInArena(loc)) {
            return false;
        }
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (barrierLocations.contains(key) || manager.getArenaManager().isProtectedBlock(loc)) {
            return false;
        }
        return true;
    }

    public boolean isEnded() {
        return ended;
    }

    public long getPearlBlockUntil() {
        return pearlBlockUntil;
    }

    public boolean isRestorePending() {
        return restorePending;
    }

    public void addPlacedBlock(Location loc, UUID playerId) {
        playerPlaced.put(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), playerId);
    }

    public void removePlacedBlock(Location loc) {
        playerPlaced.remove(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public void recordBlockChange(Location loc, BlockData originalState) {
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        originalBlocks.putIfAbsent(key, originalState);
    }

    public boolean isBlockedMaterial(Material material) {
        if (material == Material.ARROW || material == Material.SPECTRAL_ARROW || material == Material.TIPPED_ARROW) {
            return request.isBlockArrows();
        }
        if (material == Material.FIREWORK_ROCKET) {
            return request.isBlockFireworks();
        }
        if (material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) {
            return request.isBlockPotions();
        }
        return false;
    }

    public boolean isBlockArrows() {
        return request.isBlockArrows();
    }

    public boolean isBlockFireworks() {
        return request.isBlockFireworks();
    }

    public boolean isBlockPotions() {
        return request.isBlockPotions();
    }

    public boolean isBlockSlowFalling() {
        return request.isBlockSlowFalling();
    }

    public void finishDuel(Player winner, Player loser, String reason) {
        if (ended) {
            return;
        }
        // block ender-pearls for a short cooldown after duel ends
        pearlBlockUntil = System.currentTimeMillis() + 10_000L;
        ended = true;
        try {
            manager.getScoreboardService().removeScoreboardForSession(this);
        } catch (Exception ignored) {}
        if (winner != null) {
            manager.recordWin(winner);
        }
        if (!request.isKeepInventory() && winner != null && loser != null) {
            applyLifeSteal(winner, loser);
        }
        if (request.isKeepInventory()) {
            cleanupArenaEntities();
        }
        
        if (winner != null && winner.isOnline()) {
            String title = configManager.getDuelEndWinnerTitle();
            String subtitle = configManager.getDuelEndWinnerSubtitle().replace("{loser}", loser != null ? loser.getName() : "opponent");
            winner.sendTitle(title, subtitle, 10, 100, 30);
            if (configManager.isDuelEndSoundEnabled()) {
                try {
                    winner.playSound(winner.getLocation(), Sound.valueOf(configManager.getDuelEndWinnerSound()), 1.0f, configManager.getDuelEndSoundPitch());
                } catch (IllegalArgumentException ignored) {
                }
            }
            winner.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, configManager.getDuelEndBlindnessDuration() * 20, 0, false, false));
            
            // Victory particles
            Location winLoc = winner.getLocation().add(0, 1.5, 0);
            winner.spawnParticle(Particle.FLAME, winLoc, 100, 1, 1, 1, 0.2);
            winner.spawnParticle(Particle.FIREWORK, winLoc, 60, 1.5, 1.5, 1.5, 0.25);
            winner.spawnParticle(Particle.ENCHANT, winLoc, 50, 1, 1, 1, 0.15);
            
            winner.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
        }
        
        if (loser != null && loser.isOnline()) {
            String title = configManager.getDuelEndLoserTitle();
            String subtitle = configManager.getDuelEndLoserSubtitle().replace("{winner}", winner != null ? winner.getName() : "opponent");
            loser.sendTitle(title, subtitle, 10, 100, 30);
            if (configManager.isDuelEndSoundEnabled()) {
                try {
                    loser.playSound(loser.getLocation(), Sound.valueOf(configManager.getDuelEndLoserSound()), 1.0f, configManager.getDuelEndSoundPitch());
                } catch (IllegalArgumentException ignored) {
                }
            }
            loser.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, configManager.getDuelEndBlindnessDuration() * 20, 0, false, false));
            
            // Defeat particles
            Location loseLoc = loser.getLocation().add(0, 1.5, 0);
            loser.spawnParticle(Particle.SMOKE, loseLoc, 80, 1, 1, 1, 0.15);
            loser.spawnParticle(Particle.CRIT, loseLoc, 40, 0.8, 0.8, 0.8, 0.1);
            loser.spawnParticle(Particle.SMOKE, loseLoc, 30, 1.5, 1.5, 1.5, 0.2);
        }
        
        restoreArena();
        if (request.getArenaName() != null) {
            manager.getArenaManager().regenerateArena(request.getArenaName());
        }

        restorePending = true;
        if (winner != null && winner.isOnline()) {
            winner.sendMessage(PREFIX + "§eInventory restoration will begin in 2 seconds...");
        }
        if (loser != null && loser.isOnline()) {
            loser.sendMessage(PREFIX + "§eInventory restoration will begin in 2 seconds...");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    restoreAvailablePlayers();
                    teleportBackNow();
                    restoreWorldBorder();
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error completing duel cleanup: " + ex.getMessage());
                } finally {
                    restorePending = false;
                    cleanupIfDone();
                }
            }
        }.runTaskLater(plugin, 40L);
        
        if (winner != null && winner.isOnline()) {
            winner.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
            winner.sendMessage(PREFIX + "§a✨ DUEL VICTORY! ✨");
            winner.sendMessage(PREFIX + "§7Reason: §e" + reason);
            winner.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
        }
        if (loser != null && loser.isOnline()) {
            loser.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
            loser.sendMessage(PREFIX + "§c✗ DUEL DEFEAT ✗");
            loser.sendMessage(PREFIX + "§7Reason: §e" + reason);
            loser.sendMessage(PREFIX + "§6━━━━━━━━━━━━━━━━━━━━━━━━");
        }
        if (winner != null) {
            manager.promptRatingIfNeeded(winner);
        }
        if (loser != null) {
            manager.promptRatingIfNeeded(loser);
        }
    }

    public void endDuelQuietly() {
        if (ended) {
            return;
        }
        pearlBlockUntil = System.currentTimeMillis() + 10_000L;
        ended = true;
        try {
            restoreArena();
            restoreAvailablePlayers();
            teleportBackNow();
            restoreWorldBorder();
        } catch (Exception ex) {
            plugin.getLogger().warning("Error ending duel quietly: " + ex.getMessage());
        } finally {
            restorePending = false;
            try { manager.getScoreboardService().removeScoreboardForSession(this); } catch (Exception ignored) {}
            manager.removeSession(this);
        }
    }

    private void restoreWorldBorder() {
        if (!worldBorderApplied) {
            return;
        }
        try {
            org.bukkit.WorldBorder wb = playerA.getWorld().getWorldBorder();
            wb.setCenter(previousBorderCenterX, previousBorderCenterZ);
            wb.setSize(previousBorderSize);
        } catch (Exception ignored) {
        }
        worldBorderApplied = false;
    }

    private void cleanupArenaEntities() {
        Arena arena = manager.getArenaManager().getArena(request.getArenaName());
        if (arena == null) return;
        org.bukkit.World world = arena.getCenter() != null ? arena.getCenter().getWorld() : null;
        if (world == null) return;
        int minX, minY, minZ, maxX, maxY, maxZ;
        if (arena.hasCorners() && arena.getCorner1() != null && arena.getCorner2() != null && arena.getCorner1().getWorld().equals(arena.getCorner2().getWorld())) {
            minX = Math.min(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            maxX = Math.max(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            minY = Math.min(arena.getCorner1().getBlockY(), arena.getCorner2().getBlockY());
            maxY = Math.max(arena.getCorner1().getBlockY(), arena.getCorner2().getBlockY());
            minZ = Math.min(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
            maxZ = Math.max(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
        } else {
            Location center = request.getArenaCenter();
            if (center == null) return;
            minX = center.getBlockX() - 8;
            maxX = center.getBlockX() + 8;
            minY = center.getBlockY() - 4;
            maxY = center.getBlockY() + 8;
            minZ = center.getBlockZ() - 8;
            maxZ = center.getBlockZ() + 8;
        }
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof org.bukkit.entity.Player) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() < minX || loc.getBlockX() > maxX || loc.getBlockY() < minY || loc.getBlockY() > maxY || loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) {
                continue;
            }
            if (entity instanceof org.bukkit.entity.Item
                    || entity instanceof org.bukkit.entity.ExperienceOrb
                    || entity instanceof org.bukkit.entity.Projectile
                    || entity instanceof org.bukkit.entity.ArmorStand
                    || entity instanceof org.bukkit.entity.FallingBlock) {
                entity.remove();
            }
        }
    }

    private void applyLifeSteal(Player winner, Player loser) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (winner != null && winner.isOnline()) {
                    double newHealth = Math.min(winner.getMaxHealth(), winner.getHealth() + 2.0);
                    winner.setHealth(newHealth);
                    winner.sendMessage(PREFIX + "§aYou gained a heart from your duel victory!");
                }
                if (loser != null && loser.isOnline()) {
                    double newHealth = Math.max(1.0, loser.getHealth() - 2.0);
                    loser.setHealth(newHealth);
                    loser.sendMessage(PREFIX + "§cYou lost a heart from the duel defeat.");
                }
            }
        }.runTaskLater(plugin, 4);
    }

    private void startLootingCountdown(Player winner, Player loser) {
        awaitingReturn = true;
        if (winner == null || !winner.isOnline()) {
            restoreArena();
            if (request.getArenaName() != null) {
                manager.getArenaManager().regenerateArena(request.getArenaName());
            }
            manager.removeSession(this);
            awaitingReturn = false;
            return;
        }

        String title = "§6LOOT MODE ACTIVE";
        String subtitle = "§72 minutes to collect items and leave safely";
        winner.sendTitle(title, subtitle, 10, 60, 20);
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.3f, 1.0f);
        winner.spawnParticle(Particle.HEART, winner.getLocation().add(0, 1.5, 0), 50, 0.8, 0.8, 0.8, 0.1);
        winner.sendMessage(PREFIX + "§eYou have 2 minutes of looting time!");
        winner.sendMessage(PREFIX + "§7Only blocks placed during the duel can be broken.");

        if (loser != null && loser.isOnline()) {
            loser.sendMessage(PREFIX + "§7You are being returned to your previous location while the winner finishes looting.");
        }

        new BukkitRunnable() {
            private int remaining = 120;

            @Override
            public void run() {
                if (winner == null || !winner.isOnline()) {
                    manager.removeSession(DuelSession.this);
                    awaitingReturn = false;
                    cancel();
                    return;
                }
                if (remaining == 50 || remaining == 30 || remaining == 10 || remaining <= 5) {
                    winner.sendTitle("§bLoot Time: " + remaining + "s", "§7Stay in the arena until the timer ends.", 5, 20, 5);
                    winner.playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                }
                if (remaining <= 0) {
                    if (winner.isOnline()) {
                        winner.sendTitle("§aReturn Incoming", "§7Teleporting you back to your original position.", 10, 40, 10);
                        winner.playSound(winner.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);
                        winner.spawnParticle(Particle.CRIT, winner.getLocation().add(0, 1.5, 0), 80, 0.5, 0.5, 0.5, 0.05);
                    }
                    playerTeleportBack(winner, "§6Loot time is over! Returning you now.");
                    restoreArena();
                    if (request.getArenaName() != null) {
                        manager.getArenaManager().regenerateArena(request.getArenaName());
                    }
                    manager.removeSession(DuelSession.this);
                    awaitingReturn = false;
                    cancel();
                    return;
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void playerTeleportBack(Player player, String message) {
        if (player == null || !player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) {
            return;
        }
        Location teleportLoc = returnLocations.get(player.getUniqueId());
        player.teleport(teleportLoc);
        player.sendMessage(PREFIX + message);
    }

    private void teleportBackNow() {
        if (playerA.isOnline() && returnLocations.containsKey(playerA.getUniqueId())) {
            Location teleportLoc = returnLocations.get(playerA.getUniqueId());
            playerA.teleport(teleportLoc);
            playerA.sendMessage(PREFIX + "§6✨ You have been teleported back to your original location.");
        }
        if (playerB.isOnline() && returnLocations.containsKey(playerB.getUniqueId())) {
            Location teleportLoc = returnLocations.get(playerB.getUniqueId());
            playerB.teleport(teleportLoc);
            playerB.sendMessage(PREFIX + "§6✨ You have been teleported back to your original location.");
        }
    }

    public void restoreForPlayer(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        PlayerInventorySnapshot backup = backups.get(id);
        if (backup != null) {
            backup.restore();
            backups.remove(id);
            returnLocations.remove(id);
        }
        Location loc = returnLocations.get(id);
        if (loc != null) {
            player.teleport(loc);
            returnLocations.remove(id);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setSpectatorTarget(null);
        player.setAllowFlight(false);
        player.setFlying(false);
        cleanupIfDone();
    }

    private void teleportBack() {
        teleportBackNow();
    }

    private void restoreInventories() {
        restoreAvailablePlayers();
    }

    private void restoreAvailablePlayers() {
        List<UUID> restored = new ArrayList<>();
        for (Map.Entry<UUID, PlayerInventorySnapshot> entry : backups.entrySet()) {
            UUID id = entry.getKey();
            PlayerInventorySnapshot backup = entry.getValue();
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                backup.restore();
                Location loc = returnLocations.get(id);
                if (loc != null) {
                    player.teleport(loc);
                }
                player.setGameMode(GameMode.SURVIVAL);
                player.setSpectatorTarget(null);
                player.setAllowFlight(false);
                player.setFlying(false);
                restored.add(id);
            }
        }
        for (UUID id : restored) {
            backups.remove(id);
            returnLocations.remove(id);
        }
    }

    private void cleanupIfDone() {
        if (!ended) {
            return;
        }
        if (restorePending) {
            if (backups.isEmpty()) {
                restorePending = false;
                try { manager.getScoreboardService().removeScoreboardForSession(this); } catch (Exception ignored) {}
                manager.removeSession(this);
            }
            return;
        }
        if (backups.isEmpty()) {
            manager.removeSession(this);
        }
    }

    private void captureArenaSnapshot(Arena arena) {
        if (arena == null) {
            return;
        }
        if (arena.hasCorners() && arena.getCorner1() != null && arena.getCorner2() != null && arena.getCorner1().getWorld().equals(arena.getCorner2().getWorld())) {
            Location c1 = arena.getCorner1();
            Location c2 = arena.getCorner2();
            int minX = Math.min(c1.getBlockX(), c2.getBlockX());
            int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
            int minY = Math.min(c1.getBlockY(), c2.getBlockY());
            int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
            int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
            int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());
            org.bukkit.World world = c1.getWorld();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Location loc = new Location(world, x, y, z);
                        Block block = loc.getBlock();
                        originalBlocks.put(loc, block.getBlockData().clone());
                    }
                }
            }
            return;
        }
        Location center = request.getArenaCenter();
        if (center == null) {
            return;
        }
        int radius = 4;
        int height = 3;
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = center.getBlockY() - 1; y <= center.getBlockY() + height; y++) {
                    Location loc = new Location(center.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    originalBlocks.put(loc, block.getBlockData().clone());
                }
            }
        }
    }

    private void createBarrier(int radius, int height) {
        Location center = request.getArenaCenter();
        java.util.List<Location> barrierLocations = new java.util.ArrayList<>();
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int y = center.getBlockY(); y <= center.getBlockY() + height; y++) {
                for (int z : new int[]{center.getBlockZ() - radius, center.getBlockZ() + radius}) {
                    barrierLocations.add(new Location(center.getWorld(), x, y, z));
                }
            }
        }
        for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
            for (int y = center.getBlockY(); y <= center.getBlockY() + height; y++) {
                for (int x : new int[]{center.getBlockX() - radius, center.getBlockX() + radius}) {
                    barrierLocations.add(new Location(center.getWorld(), x, y, z));
                }
            }
        }
        placBarriersAsync(barrierLocations, 16);
    }

    private void placBarriersAsync(java.util.List<Location> locations, int batchSize) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                if (ended || index >= locations.size()) {
                    cancel();
                    return;
                }
                int end = Math.min(index + batchSize, locations.size());
                for (int i = index; i < end; i++) {
                    placeBarrierAt(locations.get(i));
                }
                index = end;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void placeBarrierAt(Location loc) {
        Block block = loc.getBlock();
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, block.getBlockData().clone());
        }
        block.setType(Material.BARRIER);
        barrierLocations.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    private void restoreArena() {
        originalBlocks.forEach((location, blockData) -> {
            try {
                Block block = location.getBlock();
                block.setType(blockData.getMaterial());
                block.setBlockData(blockData);
            } catch (Exception ignored) {}
        });
        // Only clear barrier blocks that were not restored from originalBlocks
        for (Location loc : new HashSet<>(barrierLocations)) {
            if (!originalBlocks.containsKey(loc)) {
                try { loc.getBlock().setType(Material.AIR); } catch (Exception ignored) {}
            }
        }
    }

    private static class PlayerInventorySnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final UUID playerId;

        public PlayerInventorySnapshot(Player player) {
            this.playerId = player.getUniqueId();
            this.contents = cloneItems(player.getInventory().getContents());
            this.armor = cloneItems(player.getInventory().getArmorContents());
            this.extra = cloneItems(player.getInventory().getExtraContents());
        }

        public void restore() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            player.getInventory().clear();
            player.getInventory().setContents(cloneItems(contents));
            player.getInventory().setArmorContents(cloneItems(armor));
            player.getInventory().setExtraContents(cloneItems(extra));
            player.updateInventory();
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
            return copy;
        }
    }
}

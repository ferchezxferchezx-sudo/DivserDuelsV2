package dev.divsersmp.duels;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamDuelSession {
    private final Party party;
    private final DuelManager manager;
    private final ConfigManager configManager;
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerInventorySnapshot> backups = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private final Map<Location, UUID> playerPlaced = new HashMap<>();
    private final Set<Location> barrierLocations = new HashSet<>();
    private final Map<UUID, String> previousLuckPermsGroup = new HashMap<>();
    private final LuckPermsSupport luckPermsSupport;
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> disconnected = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private long startedAt = System.currentTimeMillis();
    private boolean ended = false;
    private boolean countdownActive = true;
    private boolean restorePending = false;
    private boolean worldBorderApplied = false;
    private double previousBorderSize = -1;
    private double previousBorderCenterX;
    private double previousBorderCenterZ;
    private long pearlBlockUntil = 0L;

    public TeamDuelSession(Party party, DuelManager manager) {
        this.party = party;
        this.manager = manager;
        this.configManager = manager.getConfigManager();
        this.plugin = manager.getPlugin();
        this.luckPermsSupport = manager.getLuckPermsSupport();
    }

    private void createBarrierFromCorners(Arena arena) {
        if (arena == null) return;
        Location c1 = arena.getCorner1();
        Location c2 = arena.getCorner2();
        Location center = arena.getCenter();
        if (c1 != null && c2 != null && c1.getWorld().equals(c2.getWorld())) {
            int minX = Math.min(c1.getBlockX(), c2.getBlockX());
            int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
            int minY = Math.min(c1.getBlockY(), c2.getBlockY());
            int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
            int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
            int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

            org.bukkit.World world = c1.getWorld();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBarrierAt(new Location(world, x, y, minZ));
                    placeBarrierAt(new Location(world, x, y, maxZ));
                }
            }
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBarrierAt(new Location(world, minX, y, z));
                    placeBarrierAt(new Location(world, maxX, y, z));
                }
            }
        } else if (center != null) {
            // fallback to a small square around center
            int radius = 4;
            int minY = center.getBlockY() - 1;
            int maxY = center.getBlockY() + 3;
            org.bukkit.World world = center.getWorld();
            for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBarrierAt(new Location(world, x, y, center.getBlockZ() - radius));
                    placeBarrierAt(new Location(world, x, y, center.getBlockZ() + radius));
                }
            }
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBarrierAt(new Location(world, center.getBlockX() - radius, y, z));
                    placeBarrierAt(new Location(world, center.getBlockX() + radius, y, z));
                }
            }
        }
    }

    private void placeBarrierAt(Location loc) {
        try {
            if (loc == null) return;
            Block block = loc.getBlock();
            if (!originalBlocks.containsKey(loc)) {
                originalBlocks.put(loc, block.getBlockData().clone());
            }
            block.setType(Material.BARRIER);
            barrierLocations.add(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        } catch (Exception ignored) {}
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
        Location center = arena.getCenter();
        if (center == null) {
            return;
        }
        int radius = 4;
        int height = 3;
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = center.getBlockY(); y <= center.getBlockY() + height; y++) {
                    Location loc = new Location(center.getWorld(), x, y, z);
                    Block block = loc.getBlock();
                    originalBlocks.put(loc, block.getBlockData().clone());
                }
            }
        }
    }

    public void start() {
        party.getMembers().forEach(member -> {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                returnLocations.put(member, player.getLocation().clone());
                backups.put(member, new PlayerInventorySnapshot(player));
                applyTeamPrefix(player);
            }
        });

        Arena arena = manager.getArenaManager().getArena(party.getArenaName());
        Location center = arena != null && arena.getCenter() != null ? arena.getCenter() : Bukkit.getWorlds().get(0).getSpawnLocation();
        Location limeSpawn = arena != null && arena.getSpawn1() != null ? arena.getSpawn1() : center.clone().add(3, 1, 0);
        Location redSpawn = arena != null && arena.getSpawn2() != null ? arena.getSpawn2() : center.clone().add(-3, 1, 0);

        List<UUID> limeMembers = new ArrayList<>();
        List<UUID> redMembers = new ArrayList<>();
        for (UUID member : party.getMembers()) {
            String team = party.getTeam(member);
            if ("lime".equalsIgnoreCase(team)) {
                limeMembers.add(member);
            } else {
                redMembers.add(member);
            }
        }

        if (arena != null && arena.hasCorners()) {
            manager.getArenaManager().saveSnapshot(arena);
        }

        for (int i = 0; i < limeMembers.size(); i++) {
            UUID member = limeMembers.get(i);
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                Location tpLoc = limeSpawn.clone().add(i * 0.8, 0, 0);
                player.teleport(tpLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
            }
        }

        for (int i = 0; i < redMembers.size(); i++) {
            UUID member = redMembers.get(i);
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                Location tpLoc = redSpawn.clone().subtract(i * 0.8, 0, 0);
                player.teleport(tpLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
            }
        }

        // Capture the arena before placing invisible barrier walls so it can be restored after the duel.
        captureArenaSnapshot(arena);
        createBarrierFromCorners(arena);

        startCountdown(center);
        applyLuckPermsTeamGroups();
        applyWorldBorder(arena, center);
        startArenaBoundaryParticles(arena);

        try { manager.getScoreboardService().applyPartyScoreboard(this); } catch (Exception ignored) {}
    }

    private void startArenaBoundaryParticles(Arena arena) {
        if (arena == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (ended) {
                    cancel();
                    return;
                }
                Location corner1 = arena.getCorner1();
                Location corner2 = arena.getCorner2();
                if (corner1 == null || corner2 == null || !corner1.getWorld().equals(corner2.getWorld())) {
                    return;
                }
                int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
                int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
                int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
                int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
                int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
                int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

                for (int x = minX; x <= maxX; x += Math.max(1, (maxX - minX) / 10)) {
                    for (int y = minY; y <= maxY; y += Math.max(1, (maxY - minY) / 6)) {
                        arena.getCenter().getWorld().spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y + 0.5, minZ + 0.5, 0, 0, 0, 0, 1);
                        arena.getCenter().getWorld().spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y + 0.5, maxZ + 0.5, 0, 0, 0, 0, 1);
                    }
                }
                for (int z = minZ; z <= maxZ; z += Math.max(1, (maxZ - minZ) / 10)) {
                    for (int y = minY; y <= maxY; y += Math.max(1, (maxY - minY) / 6)) {
                        arena.getCenter().getWorld().spawnParticle(org.bukkit.Particle.END_ROD, minX + 0.5, y + 0.5, z + 0.5, 0, 0, 0, 0, 1);
                        arena.getCenter().getWorld().spawnParticle(org.bukkit.Particle.END_ROD, maxX + 0.5, y + 0.5, z + 0.5, 0, 0, 0, 0, 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startCountdown(Location center) {
        countdownActive = true;
        this.startedAt = System.currentTimeMillis();
        final int[] countdown = {5};
        new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    String title = countdown[0] == 1 ? "§c1" : "§e" + countdown[0];
                    String subtitle = countdown[0] == 1 ? "§7Fight starts now — do not move!" : "§7Stand still and prepare for battle";
                    for (UUID member : party.getMembers()) {
                        Player player = Bukkit.getPlayer(member);
                        if (player != null && player.isOnline()) {
                            player.sendTitle(title, subtitle, 5, 18, 5);
                            if (countdown[0] == 5) {
                                player.sendMessage(ChatColor.GOLD + "The party duel begins in 5 seconds. Do not move!");
                            }
                            player.playSound(player.getLocation(), countdown[0] == 1 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
                        }
                    }
                    countdown[0]--;
                } else {
                    countdownActive = false;
                    for (UUID member : party.getMembers()) {
                        Player player = Bukkit.getPlayer(member);
                        if (player != null && player.isOnline()) {
                            player.sendTitle("§aFight!", "§7The team duel has started", 5, 30, 10);
                            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.1f);
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public long getStartedAt() {
        return startedAt;
    }

    public DuelManager getManager() {
        return manager;
    }

    public void handlePlayerDeath(Player player) {
        if (ended || player == null || !player.isOnline()) {
            return;
        }
        eliminated.add(player.getUniqueId());
        try {
            if (player != null && player.isOnline()) {
                player.setPlayerListName(ChatColor.DARK_GRAY + player.getName());
            }
        } catch (Exception ignored) {}
        boolean teamMode = party.getMembers().size() >= 3;
        Location returnLoc = returnLocations.get(player.getUniqueId());
        if (returnLoc != null) {
            try {
                // simulate throw
                player.launchProjectile(EnderPearl.class);
            } catch (Exception ignored) {}
            // teleport back after a short delay, then set spectator/survival accordingly
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    player.teleport(returnLoc);
                    if (teamMode) {
                        spectators.add(player.getUniqueId());
                        player.setGameMode(GameMode.SPECTATOR);
                        player.sendTitle("§cEliminated", "§7You are now spectating the duel", 10, 40, 10);
                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.8f);
                        Player target = findSpectatorTarget(player);
                        if (target != null) {
                            player.setSpectatorTarget(target);
                        }
                        // After 1 second, if arena spectator spawn is set, move player there
                        try {
                            Arena arena = manager.getArenaManager().getArena(party.getArenaName());
                            if (arena != null && arena.hasSpectatorSpawn()) {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    try {
                                        Location spec = arena.getSpectatorSpawn();
                                        if (spec != null) {
                                            player.teleport(spec);
                                        }
                                    } catch (Exception ex) {
                                        plugin.getLogger().warning("Failed to teleport dead player to arena spectator spawn: " + ex.getMessage());
                                    }
                                }, 20L);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.sendTitle("§cEliminated", "§7You are out of the duel", 10, 40, 10);
                        player.setSpectatorTarget(null);
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error restoring teleported player after death: " + ex.getMessage());
                }
            }, 6L);
        } else {
            if (teamMode) {
                spectators.add(player.getUniqueId());
                player.setGameMode(GameMode.SPECTATOR);
                player.sendTitle("§cEliminated", "§7You are now spectating the duel", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.8f);
                Player target = findSpectatorTarget(player);
                if (target != null) {
                    player.setSpectatorTarget(target);
                }
            } else {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendTitle("§cEliminated", "§7You are out of the duel", 10, 40, 10);
                player.setSpectatorTarget(null);
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
        if (party.getMembers().stream().filter(member -> !eliminated.contains(member)).noneMatch(member -> {
            Player onlinePlayer = Bukkit.getPlayer(member);
            return onlinePlayer != null && onlinePlayer.isOnline();
        })) {
            finishDuel("Everyone was eliminated");
            return;
        }
        String team = party.getTeam(player.getUniqueId());
        boolean teamAlive = party.getMembers().stream()
                .filter(member -> "lime".equalsIgnoreCase(party.getTeam(member)) == "lime".equalsIgnoreCase(team))
                .anyMatch(member -> {
                    Player onlinePlayer = Bukkit.getPlayer(member);
                    return onlinePlayer != null && onlinePlayer.isOnline() && !eliminated.contains(member);
                });
        if (!teamAlive) {
            finishDuel("The " + team + " team was eliminated");
        }
    }

    public void handlePlayerQuit(Player player) {
        if (ended || player == null) {
            return;
        }
        
        // If keep inventory is OFF, kill the player without restore
        if (!party.isKeepInventory()) {
            player.setHealth(0);
        }
        
        disconnected.add(player.getUniqueId());
        eliminated.add(player.getUniqueId());
        for (UUID member : party.getMembers()) {
            Player other = Bukkit.getPlayer(member);
            if (other != null && other.isOnline() && !member.equals(player.getUniqueId())) {
                other.sendTitle("§cPlayer Left", "§7The opponent has disconnected and your team advances", 10, 40, 10);
                other.playSound(other.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            }
        }
        boolean teamAlive = party.getMembers().stream()
                .filter(member -> "lime".equalsIgnoreCase(party.getTeam(member)) == "lime".equalsIgnoreCase(party.getTeam(player.getUniqueId())))
                .anyMatch(member -> {
                    Player onlinePlayer = Bukkit.getPlayer(member);
                    return onlinePlayer != null && onlinePlayer.isOnline() && !eliminated.contains(member);
                });
        if (!teamAlive) {
            finishDuel("The " + party.getTeam(player.getUniqueId()) + " team was disqualified");
        }
    }
    
    public void handlePlayerLeave(Player player) {
        if (ended || player == null || !player.isOnline()) {
            return;
        }
        
        // Eliminate the player and teleport them back
        eliminated.add(player.getUniqueId());
        Location returnLoc = returnLocations.get(player.getUniqueId());
        if (returnLoc != null) {
            player.teleport(returnLoc);
        }
        
        // Restore inventory if keep inventory is ON
        if (party.isKeepInventory()) {
            PlayerInventorySnapshot backup = backups.get(player.getUniqueId());
            if (backup != null) {
                backup.restore();
            }
        }
        
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(ChatColor.GREEN + "You were teleported back to your previous location.");
        
        // Check if team is now eliminated
        String playerTeam = party.getTeam(player.getUniqueId());
        boolean teamAlive = party.getMembers().stream()
                .filter(member -> playerTeam.equalsIgnoreCase(party.getTeam(member)))
                .anyMatch(member -> {
                    Player onlinePlayer = Bukkit.getPlayer(member);
                    return onlinePlayer != null && onlinePlayer.isOnline() && !eliminated.contains(member);
                });
        
        if (!teamAlive) {
            finishDuel("A player from the " + playerTeam + " team left the duel");
        }
    }
    
    public void handlePartyDisband() {
        if (ended) {
            return;
        }
        
        // Teleport all members back and restore if keep inventory is ON
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                Location returnLoc = returnLocations.get(member);
                if (returnLoc != null) {
                    player.teleport(returnLoc);
                }
                
                if (party.isKeepInventory()) {
                    PlayerInventorySnapshot backup = backups.get(member);
                    if (backup != null) {
                        backup.restore();
                    }
                }
                
                player.setGameMode(GameMode.SURVIVAL);
                player.setSpectatorTarget(null);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage(ChatColor.GREEN + "Party has been disbanded. You were returned to your previous location.");
            }
        }
        
        ended = true;
        restoreLuckPermsGroups();
        restoreWorldBorder();
        if (party.getArenaName() != null) {
            manager.getArenaManager().regenerateArena(party.getArenaName());
        }
        restoreLuckPermsGroups();
        restoreWorldBorder();
        try { manager.getScoreboardService().removeScoreboardForSession(this); } catch (Exception ignored) {}
        manager.removePartySession(this);
    }

    public void finishDuel(String reason) {
        if (ended) {
            return;
        }
        // block ender-pearls for a short cooldown after duel ends
        pearlBlockUntil = System.currentTimeMillis() + 10_000L;
        ended = true;
        String winnerTeam = determineWinnerTeam();
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null || !player.isOnline()) {
                continue;
            }
            resetTeamPrefix(player);
            if (winnerTeam != null) {
                String team = party.getTeam(member);
                if (winnerTeam.equalsIgnoreCase(team)) {
                    String title = configManager.getDuelEndWinnerTitle();
                    String subtitle = configManager.getDuelEndWinnerSubtitle().replace("{loser}", "the other team");
                    player.sendTitle(title, subtitle, 10, 60, 20);
                    if (configManager.isDuelEndSoundEnabled()) {
                        try {
                            player.playSound(player.getLocation(), Sound.valueOf(configManager.getDuelEndWinnerSound()), 1.0f, configManager.getDuelEndSoundPitch());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                } else {
                    String title = configManager.getDuelEndLoserTitle();
                    String subtitle = configManager.getDuelEndLoserSubtitle().replace("{winner}", "the winning team");
                    player.sendTitle(title, subtitle, 10, 60, 20);
                    if (configManager.isDuelEndSoundEnabled()) {
                        try {
                            player.playSound(player.getLocation(), Sound.valueOf(configManager.getDuelEndLoserSound()), 1.0f, configManager.getDuelEndSoundPitch());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, configManager.getDuelEndBlindnessDuration() * 20, 0, false, false));
            }
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GREEN + "Team Duel Finished");
            player.sendMessage(ChatColor.GRAY + "Reason: " + ChatColor.YELLOW + reason);
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━");
        }
        if (party.isKeepInventory()) {
            cleanupArenaEntities();
        }
        restoreArena();
        restoreLuckPermsGroups();
        restoreWorldBorder();
        if (party.getArenaName() != null) {
            manager.getArenaManager().regenerateArena(party.getArenaName());
        }
        try { manager.getScoreboardService().removeScoreboardForSession(this); } catch (Exception ignored) {}
        restorePending = true;
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "Inventory restoration and teleportation will occur in 2 seconds...");
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    restoreAvailablePlayers();
                    // restore leader's party-creation inventory if present
                    try {
                        PartyManager pm = manager.getPartyManager();
                        Player leader = Bukkit.getPlayer(party.getLeaderId());
                        if (leader != null && leader.isOnline()) {
                            pm.restorePartyCreationInventory(leader);
                        }
                    } catch (Exception ignored) {}
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error restoring party duel players: " + ex.getMessage());
                } finally {
                    restorePending = false;
                    manager.removePartySession(TeamDuelSession.this);
                }
            }
        }.runTaskLater(plugin, 40L);
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
        try { player.setPlayerListName(player.getName()); } catch (Exception ignored) {}
        cleanupIfDone();
    }

    public long getPearlBlockUntil() {
        return pearlBlockUntil;
    }

    private void restoreArena() {
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location location = entry.getKey();
            BlockData originalState = entry.getValue();
            Block block = location.getBlock();
            block.setType(originalState.getMaterial());
            block.setBlockData(originalState);
        }
        // Only clear barrier blocks that were not restored from originalBlocks
        try {
            for (Location loc : new HashSet<>(barrierLocations)) {
                if (!originalBlocks.containsKey(loc)) {
                    if (loc != null && loc.getBlock() != null) {
                        loc.getBlock().setType(Material.AIR);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void restoreAvailablePlayers() {
        List<UUID> restored = new ArrayList<>();
        // Ensure all party members are restored even if backups map is missing entries
        for (UUID member : new ArrayList<>(party.getMembers())) {
            try {
                Player player = Bukkit.getPlayer(member);
                if (player == null || !player.isOnline()) continue;
                PlayerInventorySnapshot backup = backups.get(member);
                if (backup != null) {
                    backup.restore();
                }
                Location loc = returnLocations.get(member);
                if (loc != null) {
                    player.teleport(loc);
                }
                player.setGameMode(GameMode.SURVIVAL);
                player.setSpectatorTarget(null);
                player.setAllowFlight(false);
                player.setFlying(false);
                try { player.setPlayerListName(player.getName()); } catch (Exception ignored) {}
                restored.add(member);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error restoring player after duel: " + ex.getMessage());
            }
        }
        for (UUID id : restored) {
            backups.remove(id);
            returnLocations.remove(id);
        }
        restorePending = false;
    }

    private void cleanupIfDone() {
        if (ended && !restorePending && backups.isEmpty()) {
            manager.removePartySession(TeamDuelSession.this);
        }
    }

    private void applyLuckPermsTeamGroups() {
        if (!luckPermsSupport.isAvailable()) {
            plugin.getLogger().info("LuckPerms not available; skipping team group assignment.");
            return;
        }
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null || !player.isOnline()) {
                continue;
            }
            String previousGroup = luckPermsSupport.getPrimaryGroup(member);
            if (previousGroup != null && !previousGroup.isBlank()) {
                previousLuckPermsGroup.putIfAbsent(member, previousGroup);
            }
            String team = party.getTeam(member);
            if ("lime".equalsIgnoreCase(team) || "red".equalsIgnoreCase(team)) {
                boolean success = luckPermsSupport.setPrimaryGroup(member, team);
                plugin.getLogger().info("LuckPerms: assigned player " + player.getName() + " to group " + team + " (success=" + success + ").");
            }
        }
    }

    private void restoreLuckPermsGroups() {
        if (!luckPermsSupport.isAvailable()) {
            return;
        }
        for (Map.Entry<UUID, String> entry : previousLuckPermsGroup.entrySet()) {
            UUID member = entry.getKey();
            String previousGroup = entry.getValue();
            if (previousGroup == null || previousGroup.isBlank()) {
                continue;
            }
            boolean success = luckPermsSupport.setPrimaryGroup(member, previousGroup);
            Player player = Bukkit.getPlayer(member);
            String name = player != null ? player.getName() : member.toString();
            plugin.getLogger().info("LuckPerms: restored player " + name + " to group " + previousGroup + " (success=" + success + ").");
        }
        previousLuckPermsGroup.clear();
    }

    private void applyWorldBorder(Arena arena, Location center) {
        if (arena == null || center == null) {
            return;
        }
        try {
            dev.divsersmp.duels.ArenaManager.WorldBorderState prev = manager.getArenaManager().applyWorldBorder(arena);
            if (prev != null) {
                worldBorderApplied = true;
                previousBorderCenterX = prev.getCenterX();
                previousBorderCenterZ = prev.getCenterZ();
                previousBorderSize = prev.getSize();
            }
        } catch (Exception ignored) {}
    }

    private void restoreWorldBorder() {
        if (!worldBorderApplied) {
            return;
        }
        Arena arena = manager.getArenaManager().getArena(party.getArenaName());
        if (arena == null || arena.getCenter() == null) {
            worldBorderApplied = false;
            return;
        }
        try {
            org.bukkit.WorldBorder wb = arena.getCenter().getWorld().getWorldBorder();
            wb.setCenter(previousBorderCenterX, previousBorderCenterZ);
            wb.setSize(previousBorderSize);
        } catch (Exception ignored) {
        }
        worldBorderApplied = false;
    }

    private void cleanupArenaEntities() {
        Arena arena = manager.getArenaManager().getArena(party.getArenaName());
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
            Location center = arena.getCenter();
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

    public boolean isKeepInventorySession() {
        return party.isKeepInventory();
    }

    public boolean isRestorePending() {
        return restorePending;
    }

    public void recordBlockChange(Location loc, BlockData originalState) {
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        originalBlocks.putIfAbsent(key, originalState);
    }

    public void addPlacedBlock(Location loc, UUID playerId) {
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        playerPlaced.put(key, playerId);
    }

    public void removePlacedBlock(Location loc) {
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        playerPlaced.remove(key);
    }

    public boolean canBreakBlock(Location loc, Player player) {
        if (ended) return false;
        if (player == null) return false;
        if (!isInArena(loc)) return false;
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (barrierLocations.contains(key) || manager.getArenaManager().isProtectedBlock(loc)) {
            return false;
        }
        return true;
    }

    public boolean isInArena(Location location) {
        if (location == null) return false;
        Arena arena = manager.getArenaManager().getArena(party.getArenaName());
        return arena != null && manager.getArenaManager().isLocationInsideArena(arena, location);
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public Party getParty() {
        return party;
    }

    public boolean isEliminated(UUID playerId) {
        return playerId != null && eliminated.contains(playerId);
    }

    private String determineWinnerTeam() {
        boolean limeAlive = party.getMembers().stream().filter(member -> "lime".equalsIgnoreCase(party.getTeam(member))).anyMatch(member -> {
            Player player = Bukkit.getPlayer(member);
            return player != null && player.isOnline() && !eliminated.contains(member);
        });
        boolean redAlive = party.getMembers().stream().filter(member -> "red".equalsIgnoreCase(party.getTeam(member))).anyMatch(member -> {
            Player player = Bukkit.getPlayer(member);
            return player != null && player.isOnline() && !eliminated.contains(member);
        });
        if (limeAlive && !redAlive) {
            return "lime";
        }
        if (redAlive && !limeAlive) {
            return "red";
        }
        return null;
    }

    private Player findSpectatorTarget(Player deadPlayer) {
        for (UUID member : party.getMembers()) {
            if (member.equals(deadPlayer.getUniqueId())) {
                continue;
            }
            Player other = Bukkit.getPlayer(member);
            if (other != null && other.isOnline() && !eliminated.contains(member)) {
                return other;
            }
        }
        return null;
    }

    private void applyTeamPrefix(Player player) {
        String team = party.getTeam(player.getUniqueId());
        String prefix = "lime".equalsIgnoreCase(team) ? "§a" : "§c";
        player.setDisplayName(prefix + player.getName());
        player.setPlayerListName(prefix + player.getName());
        player.setCustomName(prefix + player.getName());
        player.setCustomNameVisible(true);
    }

    private void resetTeamPrefix(Player player) {
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        player.setCustomName(player.getName());
        player.setCustomNameVisible(false);
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

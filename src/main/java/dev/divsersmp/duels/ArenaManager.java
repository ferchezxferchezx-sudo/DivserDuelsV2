package dev.divsersmp.duels;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {
    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private final Map<String, Map<Location, org.bukkit.block.data.BlockData>> protectedBlocks = new ConcurrentHashMap<>();
    private File arenasFile;
    private FileConfiguration config;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        // load plugin config if present
        try {
            File cfgFile = new File(plugin.getDataFolder(), "config.yml");
            if (cfgFile.exists()) {
                this.config = YamlConfiguration.loadConfiguration(cfgFile);
            } else {
                this.config = plugin.getConfig();
            }
        } catch (Exception ignored) {
            this.config = plugin.getConfig();
        }
        loadArenas();
    }

    public void loadArenas() {
        if (!arenasFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        for (String key : config.getKeys(false)) {
            if (config.isConfigurationSection(key)) {
                String worldName = config.getString(key + ".world");
                double x = config.getDouble(key + ".x");
                double y = config.getDouble(key + ".y");
                double z = config.getDouble(key + ".z");
                Arena arena = new Arena(key, worldName, x, y, z);
                
                // Load spawn1
                if (config.isConfigurationSection(key + ".spawn1")) {
                    String spawn1World = config.getString(key + ".spawn1.world");
                    double spawn1X = config.getDouble(key + ".spawn1.x");
                    double spawn1Y = config.getDouble(key + ".spawn1.y");
                    double spawn1Z = config.getDouble(key + ".spawn1.z");
                    World w1 = Bukkit.getWorld(spawn1World);
                    if (w1 != null) {
                        arena.setSpawn1(new Location(w1, spawn1X, spawn1Y, spawn1Z));
                    }
                }
                
                // Load spawn2
                if (config.isConfigurationSection(key + ".spawn2")) {
                    String spawn2World = config.getString(key + ".spawn2.world");
                    double spawn2X = config.getDouble(key + ".spawn2.x");
                    double spawn2Y = config.getDouble(key + ".spawn2.y");
                    double spawn2Z = config.getDouble(key + ".spawn2.z");
                    World w2 = Bukkit.getWorld(spawn2World);
                    if (w2 != null) {
                        arena.setSpawn2(new Location(w2, spawn2X, spawn2Y, spawn2Z));
                    }
                }
                
                // Load schematic path
                if (config.contains(key + ".icon")) {
                    String iconName = config.getString(key + ".icon");
                    try {
                        arena.setIconMaterial(Material.valueOf(iconName));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (config.isConfigurationSection(key + ".spectator")) {
                    String spectatorWorld = config.getString(key + ".spectator.world");
                    double spectatorX = config.getDouble(key + ".spectator.x");
                    double spectatorY = config.getDouble(key + ".spectator.y");
                    double spectatorZ = config.getDouble(key + ".spectator.z");
                    World w3 = Bukkit.getWorld(spectatorWorld);
                    if (w3 != null) {
                        arena.setSpectatorSpawn(new Location(w3, spectatorX, spectatorY, spectatorZ));
                    }
                }
                if (config.contains(key + ".schematic")) {
                    arena.setSchematicPath(config.getString(key + ".schematic"));
                }
                if (config.contains(key + ".worldborder")) {
                    arena.setWorldBorderPath(config.getString(key + ".worldborder"));
                }
                // Load corners
                if (config.isConfigurationSection(key + ".corner1")) {
                    String w = config.getString(key + ".corner1.world");
                    World cw = Bukkit.getWorld(w);
                    if (cw != null) {
                        double cx = config.getDouble(key + ".corner1.x");
                        double cy = config.getDouble(key + ".corner1.y");
                        double cz = config.getDouble(key + ".corner1.z");
                        arena.setCorner1(new Location(cw, cx, cy, cz));
                    }
                }
                if (config.isConfigurationSection(key + ".corner2")) {
                    String w2 = config.getString(key + ".corner2.world");
                    World cw2 = Bukkit.getWorld(w2);
                    if (cw2 != null) {
                        double cx2 = config.getDouble(key + ".corner2.x");
                        double cy2 = config.getDouble(key + ".corner2.y");
                        double cz2 = config.getDouble(key + ".corner2.z");
                        arena.setCorner2(new Location(cw2, cx2, cy2, cz2));
                    }
                }
                // Load auto-regenerate flag
                if (config.contains(key + ".autoregen")) {
                    arena.setAutoRegenerate(config.getBoolean(key + ".autoregen", false));
                }
                
                arenas.put(key, arena);
            }
        }
    }

    public void saveArenas() {
        FileConfiguration config = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            config.set(arena.getName() + ".world", arena.getWorldName());
            config.set(arena.getName() + ".x", arena.getX());
            config.set(arena.getName() + ".y", arena.getY());
            config.set(arena.getName() + ".z", arena.getZ());
            
            // Save spawn1
            if (arena.getSpawn1() != null) {
                config.set(arena.getName() + ".spawn1.world", arena.getSpawn1().getWorld().getName());
                config.set(arena.getName() + ".spawn1.x", arena.getSpawn1().getX());
                config.set(arena.getName() + ".spawn1.y", arena.getSpawn1().getY());
                config.set(arena.getName() + ".spawn1.z", arena.getSpawn1().getZ());
            }
            
            // Save spawn2
            if (arena.getSpawn2() != null) {
                config.set(arena.getName() + ".spawn2.world", arena.getSpawn2().getWorld().getName());
                config.set(arena.getName() + ".spawn2.x", arena.getSpawn2().getX());
                config.set(arena.getName() + ".spawn2.y", arena.getSpawn2().getY());
                config.set(arena.getName() + ".spawn2.z", arena.getSpawn2().getZ());
            }
            
            // Save icon material
            if (arena.getIconMaterial() != null) {
                config.set(arena.getName() + ".icon", arena.getIconMaterial().name());
            }
            // Save spectator spawn
            if (arena.getSpectatorSpawn() != null) {
                config.set(arena.getName() + ".spectator.world", arena.getSpectatorSpawn().getWorld().getName());
                config.set(arena.getName() + ".spectator.x", arena.getSpectatorSpawn().getX());
                config.set(arena.getName() + ".spectator.y", arena.getSpectatorSpawn().getY());
                config.set(arena.getName() + ".spectator.z", arena.getSpectatorSpawn().getZ());
            }
            // Save schematic path
            if (arena.getSchematicPath() != null) {
                config.set(arena.getName() + ".schematic", arena.getSchematicPath());
            }
            if (arena.getWorldBorderPath() != null) {
                config.set(arena.getName() + ".worldborder", arena.getWorldBorderPath());
            }
            // Save corners
            if (arena.getCorner1() != null) {
                config.set(arena.getName() + ".corner1.world", arena.getCorner1().getWorld().getName());
                config.set(arena.getName() + ".corner1.x", arena.getCorner1().getX());
                config.set(arena.getName() + ".corner1.y", arena.getCorner1().getY());
                config.set(arena.getName() + ".corner1.z", arena.getCorner1().getZ());
            }
            if (arena.getCorner2() != null) {
                config.set(arena.getName() + ".corner2.world", arena.getCorner2().getWorld().getName());
                config.set(arena.getName() + ".corner2.x", arena.getCorner2().getX());
                config.set(arena.getName() + ".corner2.y", arena.getCorner2().getY());
                config.set(arena.getName() + ".corner2.z", arena.getCorner2().getZ());
            }
            config.set(arena.getName() + ".autoregen", arena.isAutoRegenerate());
        }
        try {
            config.save(arenasFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save arenas: " + e.getMessage());
        }
    }

    public void createArena(String name, Location center) {
        if (arenas.containsKey(name)) {
            return;
        }
        Arena arena = new Arena(name, center);
        arenas.put(name, arena);
        saveArenas();
    }

    public void deleteArena(String name) {
        if (arenas.remove(name) != null) {
            saveArenas();
        }
    }

    public Arena getArena(String name) {
        return arenas.get(name);
    }

    public Collection<String> listArenas() {
        return new ArrayList<>(arenas.keySet());
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name);
    }

    public void setSpawn(String arenaName, int spawnNumber, Location location) {
        Arena arena = arenas.get(arenaName);
        if (arena != null) {
            if (spawnNumber == 1) {
                arena.setSpawn1(location);
            } else if (spawnNumber == 2) {
                arena.setSpawn2(location);
            }
            saveArenas();
        }
    }

    public void setSpawnByLabel(String arenaName, String label, Location location) {
        if (label == null) {
            return;
        }
        String normalized = label.equalsIgnoreCase("lime") ? "1" : label.equalsIgnoreCase("red") ? "2" : label;
        try {
            setSpawn(arenaName, Integer.parseInt(normalized), location);
        } catch (NumberFormatException ignored) {
            // ignored
        }
    }

    public void setSchematicPath(String arenaName, String path) {
        Arena arena = arenas.get(arenaName);
        if (arena != null) {
            arena.setSchematicPath(path);
            saveArenas();
        }
    }

    public boolean saveSchematic(Arena arena) {
        if (arena == null || !arena.hasCorners()) {
            return false;
        }
        Location c1 = arena.getCorner1();
        Location c2 = arena.getCorner2();
        if (c1 == null || c2 == null || !c1.getWorld().equals(c2.getWorld())) {
            return false;
        }
        org.bukkit.World world = c1.getWorld();
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        File schematicDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicDir.exists()) {
            schematicDir.mkdirs();
        }
        File schematicFile = new File(schematicDir, arena.getName() + ".schem");

        try {
            if (WorldEdit.getInstance() == null) {
                plugin.getLogger().warning("WorldEdit instance not available, falling back to snapshot save.");
                saveSnapshot(arena);
                arena.setSchematicPath(null);
                saveArenas();
                return true;
            }
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(world), BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(BlockVector3.at(minX, minY, minZ));
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, clipboard.getOrigin());
            Operations.complete(copy);
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                format = ClipboardFormats.findByAlias("schematic");
            }
            if (format == null) {
                format = ClipboardFormats.findByAlias("schem");
            }
            if (format == null) {
                format = ClipboardFormats.findByAlias("sponge");
            }
            if (format == null) {
                plugin.getLogger().warning("Could not determine clipboard format for schematic file.");
                return false;
            }
            try (ClipboardWriter writer = format.getWriter(new FileOutputStream(schematicFile))) {
                writer.write(clipboard);
            }
            arena.setSchematicPath("schematics/" + arena.getName() + ".schem");
            saveArenas();
            return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save schematic via WorldEdit: " + e.getMessage() + ". Falling back to snapshot.");
            e.printStackTrace();
            try {
                saveSnapshot(arena);
                arena.setSchematicPath(null);
                saveArenas();
                return true;
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to save snapshot fallback: " + ex.getMessage());
                return false;
            }
        }
    }

    public boolean saveWorldBorder(Arena arena) {
        if (arena == null || !arena.hasWorldBorderCorners()) {
            return false;
        }
        Location c1 = arena.getCorner1();
        Location c2 = arena.getCorner2();
        if (c1 == null || c2 == null || !c1.getWorld().equals(c2.getWorld())) {
            return false;
        }
        File borderDir = new File(plugin.getDataFolder(), "worldborders");
        if (!borderDir.exists() && !borderDir.mkdirs()) {
            return false;
        }
        File borderFile = new File(borderDir, arena.getName() + ".yml");
        FileConfiguration borderConfig = new YamlConfiguration();
        borderConfig.set("world", c1.getWorld().getName());
        borderConfig.set("corner1.world", c1.getWorld().getName());
        borderConfig.set("corner1.x", c1.getBlockX());
        borderConfig.set("corner1.y", c1.getBlockY());
        borderConfig.set("corner1.z", c1.getBlockZ());
        borderConfig.set("corner2.world", c2.getWorld().getName());
        borderConfig.set("corner2.x", c2.getBlockX());
        borderConfig.set("corner2.y", c2.getBlockY());
        borderConfig.set("corner2.z", c2.getBlockZ());
        try {
            borderConfig.save(borderFile);
            arena.setWorldBorderPath("worldborders/" + arena.getName() + ".yml");
            saveArenas();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save world border: " + e.getMessage());
            return false;
        }
    }

    public boolean pasteSchematic(Arena arena) {
        if (arena == null || arena.getSchematicPath() == null) {
            return false;
        }
        File schematicFile = new File(plugin.getDataFolder(), arena.getSchematicPath());
        return pasteSchematic(arena, schematicFile);
    }

    public boolean isLocationInsideArena(Arena arena, Location location) {
        if (arena == null || location == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(arena.getWorldName())) {
            return false;
        }
        if (arena.hasCorners() && arena.getCorner1() != null && arena.getCorner2() != null) {
            Location c1 = arena.getCorner1();
            Location c2 = arena.getCorner2();
            int minX = Math.min(c1.getBlockX(), c2.getBlockX());
            int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
            int minY = Math.min(c1.getBlockY(), c2.getBlockY());
            int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
            int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
            int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());
            return location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY && location.getBlockY() <= maxY
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }
        Location center = arena.getCenter();
        if (center == null) {
            return false;
        }
        int radius = 4;
        int maxY = center.getBlockY() + 3;
        int minY = center.getBlockY() - 1;
        return Math.abs(location.getBlockX() - center.getBlockX()) <= radius
                && Math.abs(location.getBlockZ() - center.getBlockZ()) <= radius
                && location.getBlockY() >= minY
                && location.getBlockY() <= maxY;
    }

    private boolean pasteSchematic(Arena arena, File schematicFile) {
        if (arena == null || schematicFile == null || !schematicFile.exists()) {
            return false;
        }
        Location destination = getMinimumCorner(arena);
        if (destination == null) {
            return false;
        }
        org.bukkit.World world = destination.getWorld();
        if (world == null) {
            return false;
        }
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                format = ClipboardFormats.findByAlias("schematic");
            }
            if (format == null) {
                format = ClipboardFormats.findByAlias("schem");
            }
            if (format == null) {
                format = ClipboardFormats.findByAlias("sponge");
            }
            if (format == null) {
                plugin.getLogger().warning("Could not determine clipboard format for schematic file.");
                return false;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                Clipboard clipboard = reader.read();
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operations.complete(holder.createPaste(editSession)
                        .to(BlockVector3.at(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .build());
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private Location getMinimumCorner(Arena arena) {
        if (arena == null || !arena.hasCorners()) {
            return null;
        }
        Location c1 = arena.getCorner1();
        Location c2 = arena.getCorner2();
        if (c1 == null || c2 == null || !c1.getWorld().equals(c2.getWorld())) {
            return null;
        }
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        return new Location(c1.getWorld(), minX, minY, minZ);
    }

    public static class WorldBorderState {
        private final double centerX;
        private final double centerZ;
        private final double size;

        public WorldBorderState(double centerX, double centerZ, double size) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.size = size;
        }

        public double getCenterX() {
            return centerX;
        }

        public double getCenterZ() {
            return centerZ;
        }

        public double getSize() {
            return size;
        }
    }

    public WorldBorderState applyWorldBorder(Arena arena, org.bukkit.entity.Player player1, org.bukkit.entity.Player player2) {
        if (arena == null || arena.getWorldName() == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(arena.getWorldName());
        if (world == null) {
            return null;
        }
        org.bukkit.WorldBorder wb = world.getWorldBorder();
        try {
            double previousSize = wb.getSize();
            double previousCenterX = wb.getCenter().getX();
            double previousCenterZ = wb.getCenter().getZ();
            Location center = arena.getCenter();
            if (center == null) {
                return null;
            }
            double size = computeBorderSize(arena, center);
            wb.setCenter(center.getX(), center.getZ());
            wb.setSize(size);
            wb.setDamageAmount(0.0);
            wb.setDamageBuffer(1.0);
            wb.setWarningDistance(2);
            wb.setWarningTime(5);
            return new WorldBorderState(previousCenterX, previousCenterZ, previousSize);
        } catch (Exception ignored) {
            return null;
        }
    }

    public WorldBorderState applyWorldBorder(Arena arena) {
        if (arena == null || arena.getWorldName() == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(arena.getWorldName());
        if (world == null) {
            return null;
        }
        org.bukkit.WorldBorder wb = world.getWorldBorder();
        try {
            double previousSize = wb.getSize();
            double previousCenterX = wb.getCenter().getX();
            double previousCenterZ = wb.getCenter().getZ();
            Location center = arena.getCenter();
            if (center == null) {
                return null;
            }
            double size = computeBorderSize(arena, center);
            wb.setCenter(center.getX(), center.getZ());
            wb.setSize(size);
            wb.setDamageAmount(0.0);
            wb.setDamageBuffer(1.0);
            wb.setWarningDistance(2);
            wb.setWarningTime(5);
            return new WorldBorderState(previousCenterX, previousCenterZ, previousSize);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double computeBorderSize(Arena arena, Location center) {
        if (arena.hasCorners() && arena.getCorner1() != null && arena.getCorner2() != null) {
            int minX = Math.min(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            int maxX = Math.max(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            int minZ = Math.min(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
            int maxZ = Math.max(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
            double width = maxX - minX + 4;
            double depth = maxZ - minZ + 4;
            return Math.max(width, depth);
        }
        return Math.max(config.getInt("duel.border_radius", 50), 20) * 2.0;
    }

    public void setIcon(String arenaName, Material icon) {
        Arena arena = arenas.get(arenaName);
        if (arena != null) {
            arena.setIconMaterial(icon);
            saveArenas();
        }
    }

    public boolean regenerateNow(String arenaName) {
        Arena arena = arenas.get(arenaName);
        if (arena == null || !arena.hasCorners()) return false;
        if (arena.getSchematicPath() != null && pasteSchematic(arena)) {
            return true;
        }
        File snapFile = new File(new File(plugin.getDataFolder(), "snapshots"), arena.getName() + ".yml");
        if (!snapFile.exists()) return false;
        FileConfiguration snap = YamlConfiguration.loadConfiguration(snapFile);
        int originX = snap.getInt("origin.x");
        int originY = snap.getInt("origin.y");
        int originZ = snap.getInt("origin.z");
        String worldName = snap.getString("world");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return false;
        if (snap.getConfigurationSection("blocks") == null) return false;
        for (String key : snap.getConfigurationSection("blocks").getKeys(false)) {
            int ox = snap.getInt("blocks." + key + ".x");
            int oy = snap.getInt("blocks." + key + ".y");
            int oz = snap.getInt("blocks." + key + ".z");
            String matName = snap.getString("blocks." + key + ".material");
            try {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(matName);
                world.getBlockAt(originX + ox, originY + oy, originZ + oz).setType(mat);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return true;
    }

    public void setSpectatorSpawn(String arenaName, Location location) {
        Arena arena = arenas.get(arenaName);
        if (arena != null) {
            arena.setSpectatorSpawn(location);
            saveArenas();
        }
    }

    public void setCorner(String arenaName, int cornerNumber, Location location) {
        Arena arena = arenas.get(arenaName);
        if (arena == null) return;
        if (cornerNumber == 1) arena.setCorner1(location);
        else if (cornerNumber == 2) arena.setCorner2(location);
        saveArenas();
        if (arena.hasCorners()) {
            saveSnapshot(arena);
        }
    }

    public void addProtectedBlock(String arenaName, Location loc) {
        if (loc == null || arenaName == null) return;
        Map<Location, org.bukkit.block.data.BlockData> map = protectedBlocks.computeIfAbsent(arenaName, k -> new ConcurrentHashMap<>());
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (map.containsKey(key)) return;
        org.bukkit.block.Block block = key.getBlock();
        try {
            map.put(key, block.getBlockData().clone());
            block.setType(org.bukkit.Material.BARRIER);
        } catch (Exception ignored) {}
    }

    public void removeProtectedBlock(String arenaName, Location loc) {
        if (loc == null || arenaName == null) return;
        Map<Location, org.bukkit.block.data.BlockData> map = protectedBlocks.get(arenaName);
        if (map == null) return;
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        org.bukkit.block.data.BlockData original = map.remove(key);
        if (original != null) {
            try {
                key.getBlock().setType(original.getMaterial());
                key.getBlock().setBlockData(original);
            } catch (Exception ignored) {}
        }
    }

    public boolean isProtectedBlock(Location loc) {
        if (loc == null) return false;
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        for (Map<Location, org.bukkit.block.data.BlockData> map : protectedBlocks.values()) {
            if (map.containsKey(key)) return true;
        }
        return false;
    }

    public Collection<Location> getProtectedBlocksForArena(String arenaName) {
        Map<Location, org.bukkit.block.data.BlockData> map = protectedBlocks.get(arenaName);
        if (map == null) return List.of();
        return new ArrayList<>(map.keySet());
    }

    public org.bukkit.block.data.BlockData getProtectedOriginal(String arenaName, Location loc) {
        if (arenaName == null || loc == null) return null;
        Map<Location, org.bukkit.block.data.BlockData> map = protectedBlocks.get(arenaName);
        if (map == null) return null;
        Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return map.get(key);
    }

    public void setAutoRegenerate(String arenaName, boolean auto) {
        Arena arena = arenas.get(arenaName);
        if (arena == null) return;
        arena.setAutoRegenerate(auto);
        saveArenas();
    }

    public boolean saveSnapshot(Arena arena) {
        if (arena == null || !arena.hasCorners()) return false;
        Location c1 = arena.getCorner1();
        Location c2 = arena.getCorner2();
        if (c1 == null || c2 == null || !c1.getWorld().equals(c2.getWorld())) {
            return false;
        }
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());
        File snapDir = new File(plugin.getDataFolder(), "snapshots");
        if (!snapDir.exists()) snapDir.mkdirs();
        File snapFile = new File(snapDir, arena.getName() + ".yml");
        FileConfiguration snap = new YamlConfiguration();
        snap.set("origin.x", minX);
        snap.set("origin.y", minY);
        snap.set("origin.z", minZ);
        snap.set("world", c1.getWorld().getName());
        int index = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String mat = c1.getWorld().getBlockAt(x, y, z).getType().name();
                    snap.set("blocks." + index + ".x", x - minX);
                    snap.set("blocks." + index + ".y", y - minY);
                    snap.set("blocks." + index + ".z", z - minZ);
                    snap.set("blocks." + index + ".material", mat);
                    index++;
                }
            }
        }
        try {
            snap.save(snapFile);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save arena snapshot: " + e.getMessage());
            return false;
        }
    }

    public void regenerateArena(String arenaName) {
        Arena arena = arenas.get(arenaName);
        if (arena == null || !arena.hasCorners()) return;
        File snapFile = new File(new File(plugin.getDataFolder(), "snapshots"), arena.getName() + ".yml");
        if (snapFile.exists()) {
            scheduleSlowSnapshotRestore(arena, snapFile);
            return;
        }
        if (arena.getSchematicPath() != null) {
            File schematicFile = new File(plugin.getDataFolder(), arena.getSchematicPath());
            if (schematicFile.exists()) {
                pasteSchematic(arena, schematicFile);
            }
        }
    }

    private void scheduleSlowSnapshotRestore(Arena arena, File snapFile) {
        FileConfiguration snap = YamlConfiguration.loadConfiguration(snapFile);
        int originX = snap.getInt("origin.x");
        int originY = snap.getInt("origin.y");
        int originZ = snap.getInt("origin.z");
        String worldName = snap.getString("world");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        if (snap.getConfigurationSection("blocks") == null) return;

        List<BlockEntry> blocks = new ArrayList<>();
        for (String key : snap.getConfigurationSection("blocks").getKeys(false)) {
            int ox = snap.getInt("blocks." + key + ".x");
            int oy = snap.getInt("blocks." + key + ".y");
            int oz = snap.getInt("blocks." + key + ".z");
            String matName = snap.getString("blocks." + key + ".material");
            try {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(matName);
                blocks.add(new BlockEntry(originX + ox, originY + oy, originZ + oz, mat));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (blocks.isEmpty()) return;

        final int batchSize = 100;
        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                int end = Math.min(index + batchSize, blocks.size());
                for (int i = index; i < end; i++) {
                    BlockEntry entry = blocks.get(i);
                    world.getBlockAt(entry.x, entry.y, entry.z).setType(entry.material);
                }
                index = end;
                if (index >= blocks.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 4L);
    }

    private boolean slowPasteSchematic(Arena arena, File schematicFile) {
        // Use fast WorldEdit paste only if safe; otherwise let snapshot restore happen.
        // For now, if the schematic path exists, we still prefer snapshot-based slow restore.
        return false;
    }

    private static class BlockEntry {
        final int x;
        final int y;
        final int z;
        final org.bukkit.Material material;

        BlockEntry(int x, int y, int z, org.bukkit.Material material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }
    }
}

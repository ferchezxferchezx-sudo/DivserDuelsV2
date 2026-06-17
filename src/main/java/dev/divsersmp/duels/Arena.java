package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class Arena {
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private Location spawn1;
    private Location spawn2;
    private Location corner1;
    private Location corner2;
    private String schematicPath;
    private String worldBorderPath;
    private Material iconMaterial;
    private Location spectatorSpawn;
    private boolean autoRegenerate = false;

    public Arena(String name, Location center) {
        this.name = name;
        this.worldName = center.getWorld().getName();
        this.x = center.getX();
        this.y = center.getY();
        this.z = center.getZ();
    }

    public Arena(String name, String worldName, double x, double y, double z) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getName() {
        return name;
    }

    public Location getCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public String getSchematicPath() {
        return schematicPath;
    }

    public String getWorldBorderPath() {
        return worldBorderPath;
    }

    public void setWorldBorderPath(String worldBorderPath) {
        this.worldBorderPath = worldBorderPath;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(Material iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public boolean hasIconMaterial() {
        return iconMaterial != null;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public boolean hasSpectatorSpawn() {
        return spectatorSpawn != null;
    }

    public void setSchematicPath(String schematicPath) {
        this.schematicPath = schematicPath;
    }

    public boolean hasSpawns() {
        return spawn1 != null && spawn2 != null;
    }

    public Location getCorner1() {
        return corner1;
    }

    public void setCorner1(Location corner1) {
        this.corner1 = corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public void setCorner2(Location corner2) {
        this.corner2 = corner2;
    }

    public boolean hasCorners() {
        return corner1 != null && corner2 != null;
    }

    public boolean hasWorldBorderCorners() {
        return hasCorners();
    }

    public boolean isAutoRegenerate() {
        return autoRegenerate;
    }

    public void setAutoRegenerate(boolean autoRegenerate) {
        this.autoRegenerate = autoRegenerate;
    }
}

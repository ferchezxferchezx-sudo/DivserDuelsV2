package dev.divsersmp.duels;

import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DuelRequest {
    private final UUID id = UUID.randomUUID();
    private final UUID challenger;
    private final UUID target;
    private final String challengerName;
    private final String targetName;
    private boolean keepInventory = false;
    private boolean blockArrows = true;
    private boolean blockFireworks = true;
    private boolean blockPotions = true;
    private boolean blockSlowFalling = true;
    private Location arenaCenter;
    private String arenaName;
    private String kitName = "Classic";
    private String modeName = "1v1";
    private final Map<UUID, Boolean> readyState = new ConcurrentHashMap<>();
    private final long createdAt = System.currentTimeMillis();

    public DuelRequest(UUID challenger, UUID target, String challengerName, String targetName) {
        this.challenger = challenger;
        this.target = target;
        this.challengerName = challengerName;
        this.targetName = targetName;
        readyState.put(challenger, false);
        readyState.put(target, false);
    }

    public UUID getId() {
        return id;
    }

    public void setReady(UUID playerId, boolean ready) {
        readyState.put(playerId, ready);
    }

    public boolean isReady(UUID playerId) {
        return Boolean.TRUE.equals(readyState.get(playerId));
    }

    public boolean allReady() {
        return readyState.values().stream().allMatch(Boolean::booleanValue);
    }

    public UUID getChallenger() {
        return challenger;
    }

    public UUID getTarget() {
        return target;
    }

    public String getChallengerName() {
        return challengerName;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
    }

    public boolean isBlockArrows() {
        return blockArrows;
    }

    public void setBlockArrows(boolean blockArrows) {
        this.blockArrows = blockArrows;
    }

    public boolean isBlockFireworks() {
        return blockFireworks;
    }

    public void setBlockFireworks(boolean blockFireworks) {
        this.blockFireworks = blockFireworks;
    }

    public boolean isBlockPotions() {
        return blockPotions;
    }

    public void setBlockPotions(boolean blockPotions) {
        this.blockPotions = blockPotions;
    }

    public boolean isBlockSlowFalling() {
        return blockSlowFalling;
    }

    public void setBlockSlowFalling(boolean blockSlowFalling) {
        this.blockSlowFalling = blockSlowFalling;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public void setArenaCenter(Location arenaCenter) {
        this.arenaCenter = arenaCenter;
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    public String getKitName() {
        return kitName;
    }

    public void setKitName(String kitName) {
        this.kitName = kitName != null ? kitName : "Classic";
    }

    public String getModeName() {
        return modeName;
    }

    public void setModeName(String modeName) {
        this.modeName = modeName != null ? modeName : "1v1";
    }

    public String buildAllowedString() {
        StringBuilder builder = new StringBuilder();
        builder.append(isBlockArrows() ? "no arrows" : "arrows");
        builder.append(", ");
        builder.append(isBlockFireworks() ? "no fireworks" : "fireworks");
        builder.append(", ");
        builder.append(isBlockPotions() ? "no potions" : "potions");
        builder.append(", ");
        builder.append(isBlockSlowFalling() ? "no slow fall" : "slow fall");
        builder.append(", ");
        builder.append(isKeepInventory() ? "keep inventory" : "normal drops");
        return builder.toString();
    }
}

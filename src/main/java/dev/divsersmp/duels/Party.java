package dev.divsersmp.duels;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final UUID id = UUID.randomUUID();
    private final UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> readyMembers = new LinkedHashSet<>();
    private final Map<UUID, String> teamAssignments = new LinkedHashMap<>();
    private boolean keepInventory = true;
    private boolean blockArrows = false;
    private boolean blockFireworks = false;
    private boolean blockPotions = false;
    private boolean blockSlowFalling = false;
    private boolean blockMace = false;
    private String arenaName;

    public Party(UUID leaderId) {
        this.leaderId = leaderId;
        members.add(leaderId);
        teamAssignments.put(leaderId, "lime");
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
        teamAssignments.putIfAbsent(playerId, "red");
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
        teamAssignments.remove(playerId);
        readyMembers.remove(playerId);
    }

    public void setReady(UUID playerId, boolean ready) {
        if (ready) {
            readyMembers.add(playerId);
        } else {
            readyMembers.remove(playerId);
        }
    }

    public boolean isReady(UUID playerId) {
        return readyMembers.contains(playerId);
    }

    public int getReadyCount() {
        return readyMembers.size();
    }

    public boolean areAllReady() {
        return !members.isEmpty() && readyMembers.containsAll(members);
    }

    public void clearReady() {
        readyMembers.clear();
    }

    public String getTeam(UUID playerId) {
        return teamAssignments.getOrDefault(playerId, "red");
    }

    public void setTeam(UUID playerId, String team) {
        if (team == null) {
            return;
        }
        String normalized = team.equalsIgnoreCase("lime") ? "lime" : "red";
        teamAssignments.put(playerId, normalized);
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

    public boolean isBlockMace() {
        return blockMace;
    }

    public void setBlockMace(boolean blockMace) {
        this.blockMace = blockMace;
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }
}

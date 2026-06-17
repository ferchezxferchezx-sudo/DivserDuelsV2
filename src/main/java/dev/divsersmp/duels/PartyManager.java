package dev.divsersmp.duels;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {
    private final DuelManager duelManager;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private static final long INVITE_COOLDOWN_MS = 1_000L;
    private final Map<UUID, Party> partiesByLeader = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partiesByMember = new ConcurrentHashMap<>();
    private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, TeamDuelSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInventorySnapshot> partyCreationBackups = new ConcurrentHashMap<>();

    public PartyManager(DuelManager duelManager, ArenaManager arenaManager, ConfigManager configManager) {
        this.duelManager = duelManager;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
    }

    public Party createParty(Player leader) {
        Party existing = getParty(leader);
        if (existing != null) {
            leader.sendMessage(configManager.format("§cYou are already in a party."));
            return existing;
        }
        Party party = new Party(leader.getUniqueId());
        registerParty(party);
        savePartyCreationBackup(leader);
        clearPartyTools(leader);
        givePartyCreationItems(leader);

        leader.sendTitle(configManager.getTitleRaw("partystarttitle"), configManager.getTitleRaw("partystartsubtitle"), 10, 40, 10);
        leader.playSound(leader.getLocation(), Sound.valueOf(configManager.getPartyCreateSound("create_party", "ENTITY_PLAYER_LEVELUP")), 1.2f, 1.3f);
        leader.spawnParticle(org.bukkit.Particle.HEART, leader.getLocation().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0.1);
        leader.sendMessage(configManager.format("§a✓ Party created! Choose your game mode:"));
        leader.sendMessage(configManager.format("§7- §aIron Sword§7: Normal 1v1 duels"));
        leader.sendMessage(configManager.format("§7- §9Blue Armor§7: Team duels"));
        dev.divsersmp.duels.gui.PartyMenuGUI.open(leader);
        return party;
    }

    private void savePartyCreationBackup(Player player) {
        if (player == null) return;
        partyCreationBackups.put(player.getUniqueId(), new PlayerInventorySnapshot(player));
    }

    private void clearPartyTools(Player player) {
        if (player == null) return;
        try {
            int mainSlot = configManager.getPartyItemSlot("main_item", 1) - 1;
            int membersSlot = configManager.getPartyItemSlot("members_item", 2) - 1;
            int restoreSlot = configManager.getPartyItemSlot("restore_item", 9) - 1;
            if (mainSlot >= 0 && mainSlot < player.getInventory().getSize()) player.getInventory().setItem(mainSlot, null);
            if (membersSlot >= 0 && membersSlot < player.getInventory().getSize()) player.getInventory().setItem(membersSlot, null);
            if (restoreSlot >= 0 && restoreSlot < player.getInventory().getSize()) player.getInventory().setItem(restoreSlot, null);
        } catch (Exception ignored) {}
    }

    private void givePartyCreationItems(Player player) {
        if (player == null) return;
        try {
            ItemStack main = configManager.createPartyItem("main_item");
            ItemStack members = configManager.createPartyItem("members_item");
            ItemStack restore = configManager.createPartyItem("restore_item");
            int mainSlot = configManager.getPartyItemSlot("main_item", 1) - 1;
            int membersSlot = configManager.getPartyItemSlot("members_item", 2) - 1;
            int restoreSlot = configManager.getPartyItemSlot("restore_item", 9) - 1;
            if (main != null && mainSlot >= 0 && mainSlot < player.getInventory().getSize()) player.getInventory().setItem(mainSlot, main);
            if (members != null && membersSlot >= 0 && membersSlot < player.getInventory().getSize()) player.getInventory().setItem(membersSlot, members);
            if (restore != null && restoreSlot >= 0 && restoreSlot < player.getInventory().getSize()) player.getInventory().setItem(restoreSlot, restore);
        } catch (Exception ignored) {}
    }

    public void restorePartyCreationInventory(Player player) {
        if (player == null) return;
        PlayerInventorySnapshot snap = partyCreationBackups.remove(player.getUniqueId());
        if (snap != null) {
            try {
                snap.restore(player);
            } catch (Exception ignored) {}
        }
    }

    private static class PlayerInventorySnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final String world;
        private final double x, y, z;
        private final float yaw, pitch;

        public PlayerInventorySnapshot(Player player) {
            this.contents = player.getInventory().getContents().clone();
            this.armor = player.getInventory().getArmorContents().clone();
            this.extra = player.getInventory().getExtraContents().clone();
            Location loc = player.getLocation();
            this.world = loc.getWorld() != null ? loc.getWorld().getName() : null;
            this.x = loc.getX(); this.y = loc.getY(); this.z = loc.getZ();
            this.yaw = loc.getYaw(); this.pitch = loc.getPitch();
        }

        public void restore(Player player) {
            if (player == null) return;
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setExtraContents(extra);
            if (world != null) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
                if (w != null) {
                    player.teleport(new Location(w, x, y, z, yaw, pitch));
                }
            }
        }
    }

    public void invitePlayer(Player inviter, Player target) {
        Party party = getParty(inviter);
        if (party == null) {
            inviter.sendMessage(configManager.format("§cYou are not in a party."));
            return;
        }
        if (!party.isLeader(inviter.getUniqueId())) {
            inviter.sendMessage(configManager.format("§cOnly the party leader can invite players."));
            return;
        }
        if (target == null || !target.isOnline()) {
            inviter.sendMessage(configManager.format("§cThat player is not online."));
            return;
        }
        if (party.isMember(target.getUniqueId())) {
            inviter.sendMessage(configManager.format("§cThat player is already in your party."));
            return;
        }
        Long lastInvite = inviteCooldowns.get(inviter.getUniqueId());
        long remaining = lastInvite == null ? 0 : INVITE_COOLDOWN_MS - (System.currentTimeMillis() - lastInvite);
        if (remaining > 0) {
            inviter.sendMessage(configManager.format("§cPlease wait " + ((remaining + 999) / 1000) + " seconds before sending another invite."));
            return;
        }
        if (pendingInvites.containsKey(target.getUniqueId())) {
            inviter.sendMessage(configManager.format("§cThat player already has a pending invite."));
            return;
        }
        PartyInvite invite = new PartyInvite(party.getId(), inviter.getUniqueId(), target.getUniqueId());
        pendingInvites.put(target.getUniqueId(), invite);
        inviter.sendMessage(configManager.format("§aInvitation sent to §e" + target.getName()));
        target.sendMessage(configManager.format("§6You were invited to party by §e" + inviter.getName()));
        TargetComponent message = new TargetComponent(inviter, target, invite);
        message.send();
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        inviteCooldowns.put(inviter.getUniqueId(), System.currentTimeMillis());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingInvites.remove(target.getUniqueId()) == invite) {
                    target.sendMessage(configManager.format("§cParty invite expired."));
                }
            }
        }.runTaskLater(duelManager.getPlugin(), 20L * 30L);
    }

    public boolean acceptInvite(Player player, UUID inviteId) {
        PartyInvite invite = pendingInvites.get(player.getUniqueId());
        if (invite == null || !invite.getId().equals(inviteId)) {
            player.sendMessage(configManager.format("§cNo pending party invite found."));
            return false;
        }
        Party party = partiesByLeader.values().stream().filter(candidate -> candidate.getId().equals(invite.getPartyId())).findFirst().orElse(null);
        if (party == null) {
            player.sendMessage(configManager.format("§cThe party no longer exists."));
            pendingInvites.remove(player.getUniqueId());
            return false;
        }
        if (party.isMember(player.getUniqueId())) {
            player.sendMessage(configManager.format("§cYou are already in this party."));
            pendingInvites.remove(player.getUniqueId());
            return false;
        }
        party.addMember(player.getUniqueId());
        registerPartyMember(player.getUniqueId(), party);
        pendingInvites.remove(player.getUniqueId());
        player.sendTitle("§aJoined Party", "§7You are now part of the team lobby", 10, 30, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Player leader = Bukkit.getPlayer(party.getLeaderId());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(configManager.format("§e" + player.getName() + " joined the party."));
        }
        return true;
    }

    public boolean denyInvite(Player player, UUID inviteId) {
        PartyInvite invite = pendingInvites.get(player.getUniqueId());
        if (invite == null || !invite.getId().equals(inviteId)) {
            player.sendMessage(configManager.format("§cNo pending party invite found."));
            return false;
        }
        pendingInvites.remove(player.getUniqueId());
        player.sendMessage(configManager.format("§cParty invite denied."));
        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(configManager.format("§c" + player.getName() + " denied the party invite."));
        }
        return true;
    }

    public void leaveParty(Player player) {
        Party party = getParty(player);
        if (party == null) {
            player.sendMessage(configManager.format("§cYou are not in a party."));
            return;
        }
        if (party.isLeader(player.getUniqueId())) {
            disbandParty(player);
            return;
        }
        
        // Check if a duel is active and keep inventory is OFF
        TeamDuelSession activeSession = getActiveSession(player.getUniqueId());
        if (activeSession != null && !party.isKeepInventory()) {
            player.sendMessage(configManager.format("§cCannot leave party during a duel with Keep Inventory OFF."));
            return;
        }
        
        // If duel is active and keep inventory is ON, teleport back and restore
        if (activeSession != null && party.isKeepInventory()) {
            activeSession.handlePlayerLeave(player);
        }
        
        party.removeMember(player.getUniqueId());
        partiesByMember.remove(player.getUniqueId());
        player.sendMessage(configManager.format("§aYou left the party."));
        Player leader = Bukkit.getPlayer(party.getLeaderId());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(configManager.format("§e" + player.getName() + " left the party."));
        }
    }

    public void disbandParty(Player leader) {
        try {
            Party party = getParty(leader);
            if (party == null) {
                leader.sendMessage(configManager.format("§cYou are not in a party."));
                return;
            }

            // Check if a duel is active
            TeamDuelSession activeSession = getActiveSession(leader.getUniqueId());
            if (activeSession != null) {
                if (!party.isKeepInventory()) {
                    leader.sendMessage(configManager.format("§cCannot disband party during a duel with Keep Inventory OFF."));
                    return;
                }
                try {
                    activeSession.handlePartyDisband();
                } catch (Exception ex) {
                    duelManager.getPlugin().getLogger().warning("Error while handling party disband for active session: " + ex.getMessage());
                }
            }

            for (UUID member : new ArrayList<>(party.getMembers())) {
                try {
                    Player memberPlayer = Bukkit.getPlayer(member);
                    if (memberPlayer != null && memberPlayer.isOnline()) {
                        if (!member.equals(leader.getUniqueId())) {
                            memberPlayer.sendMessage(configManager.format("§cThe party has been disbanded by the leader."));
                        }
                        memberPlayer.closeInventory();
                        restorePartyCreationInventory(memberPlayer);
                    }
                } catch (Exception ex) {
                    duelManager.getPlugin().getLogger().warning("Error notifying party member during disband: " + ex.getMessage());
                }
                partiesByMember.remove(member);
            }
            restorePartyCreationInventory(leader);
            partiesByLeader.remove(party.getLeaderId());
            leader.sendMessage(configManager.format("§cYou disbanded the party."));
            leader.playSound(leader.getLocation(), Sound.valueOf(configManager.getPartyCreateSound("disband_party", "ENTITY_VILLAGER_NO")), 1.0f, 0.8f);
        } catch (Exception ex) {
            duelManager.getPlugin().getLogger().severe("Unexpected error in disbandParty: " + ex.getMessage());
            leader.sendMessage(configManager.format("§cAn unexpected error occurred while disbanding the party."));
        }
    }

    public void kickPlayer(Player leader, String targetName) {
        Party party = getParty(leader);
        if (party == null) {
            leader.sendMessage(configManager.format("§cYou are not in a party."));
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(configManager.format("§cOnly the party leader can kick members."));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            leader.sendMessage(configManager.format("§cThat player is not online."));
            return;
        }
        if (!party.isMember(target.getUniqueId())) {
            leader.sendMessage(configManager.format("§cThat player is not in your party."));
            return;
        }
        if (party.isLeader(target.getUniqueId())) {
            leader.sendMessage(configManager.format("§cYou cannot kick yourself. Use /party disband instead."));
            return;
        }
        party.removeMember(target.getUniqueId());
        partiesByMember.remove(target.getUniqueId());
        leader.sendMessage(configManager.format("§a" + target.getName() + " has been kicked from the party."));
        target.sendMessage(configManager.format("§cYou were kicked from the party by the leader."));
        target.closeInventory();
    }

    public void joinParty(Player player, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        if (leader == null || !leader.isOnline()) {
            player.sendMessage(configManager.format("§cNo online player with that name was found."));
            return;
        }
        Party party = getParty(leader);
        if (party == null) {
            player.sendMessage(configManager.format("§cThat player does not have an active party."));
            return;
        }
        invitePlayer(leader, player);
    }

    public void openSettingsMenu(Player player) {
        Party party = getParty(player);
        if (party == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 27, "Party Settings");
        fillPane(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(0, createToggleItem(Material.CHEST, "§eKeep Inventory", party.isKeepInventory()));
        inventory.setItem(2, createToggleItem(Material.ARROW, "§aBlock Arrows", party.isBlockArrows()));
        inventory.setItem(4, createToggleItem(Material.FIREWORK_ROCKET, "§aBlock Fireworks", party.isBlockFireworks()));
        inventory.setItem(6, createToggleItem(Material.POTION, "§aBlock Potions", party.isBlockPotions()));
        inventory.setItem(8, createToggleItem(Material.FEATHER, "§aBlock Slow Fall", party.isBlockSlowFalling()));
        inventory.setItem(17, createPane(Material.BARRIER, "§cBack", "§7Back to party setup"));
        inventory.setItem(26, createPremiumPane(Material.DIAMOND_SWORD, "§6Start Duel", true, "§7Start the team duel now", "§8Leader only"));
        openSmoothGui(player, inventory);
    }

    public void openPartyMenu(Player leader) {
        Inventory inventory = Bukkit.createInventory(null, 27, "Party Menu");
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(leader.getUniqueId())) {
                continue;
            }
            if (slot >= 18) {
                break;
            }
            ItemStack head = createPlayerHead(online, "§a" + online.getName(), "§7Click to invite this player");
            inventory.setItem(slot, head);
            slot++;
        }
        for (int i = slot; i < 18; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(16, createPane(Material.BARRIER, "§cLeave Party", "§7Leave the party lobby"));
        inventory.setItem(18, createPremiumPane(Material.DIAMOND_SWORD, "§6Start Duel", true, "§7Start the party duel immediately", "§8Leader only"));
        inventory.setItem(20, createPremiumPane(Material.RED_BANNER, "§6Team Setup", true, "§7Open the team menu", "§8Balance the squads for the duel"));
        inventory.setItem(26, createPremiumPane(Material.CRAFTING_TABLE, "§6Party Settings", true, "§7Adjust keep inventory and item restrictions", "§8Fine-tune the match rules"));
        leader.playSound(leader.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        openSmoothGui(leader, inventory);
    }

    public void openTeamMenu(Player leader) {
        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, "Party Teams");
        fillPane(inventory, Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 18; i < 27; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 0; i < 9; i++) {
            inventory.setItem(9 + i, createPane(Material.LIME_STAINED_GLASS_PANE, "§aLime Team"));
            inventory.setItem(27 + i, createPane(Material.RED_STAINED_GLASS_PANE, "§cRed Team"));
        }
        List<UUID> members = new ArrayList<>(party.getMembers());
        int limeSlot = 10;
        int redSlot = 28;
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player == null || !player.isOnline()) {
                continue;
            }
            ItemStack head = createPlayerHead(player, "§a" + player.getName(), "§7Click to switch teams");
            if ("lime".equalsIgnoreCase(party.getTeam(member))) {
                inventory.setItem(limeSlot, head);
                limeSlot++;
            } else {
                inventory.setItem(redSlot, head);
                redSlot++;
            }
        }
        inventory.setItem(53, createPane(Material.BARRIER, "§cBack", "§7Back to party setup"));
        leader.playSound(leader.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.1f);
        openSmoothGui(leader, inventory);
    }


    public void handlePartyMenuClick(Player player, int slot) {
        if (!player.hasPermission("divsersmp.duels.use")) {
            return;
        }
        if (slot == 16) {
            leaveParty(player);
            player.closeInventory();
            return;
        }
        if (slot == 18) {
            Party party = getParty(player);
            if (party == null) {
                return;
            }
            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage(configManager.format("§cOnly the party leader can start the duel."));
                return;
            }
            if (startPartyDuel(party)) {
                player.sendMessage(configManager.format("§aThe team duel is starting."));
            } else {
                player.sendMessage(configManager.format("§cThe party duel could not start."));
            }
            return;
        }
        if (slot == 20) {
            openTeamMenu(player);
            return;
        }
        if (slot == 26) {
            openSettingsMenu(player);
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName().replace("§a", "").trim();
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            invitePlayer(player, target);
        }
    }

    public void handleTeamMenuClick(Player player, int slot) {
        Party party = getParty(player);
        if (party == null || !party.isLeader(player.getUniqueId())) {
            return;
        }
        if (slot == 53) {
            openPartyMenu(player);
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName().replace("§a", "").trim();
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            String current = party.getTeam(target.getUniqueId());
            party.setTeam(target.getUniqueId(), "lime".equalsIgnoreCase(current) ? "red" : "lime");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openTeamMenu(player);
        }
    }

    public void handleSettingsMenuClick(Player player, int slot) {
        Party party = getParty(player);
        if (party == null) {
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage(configManager.format("§cOnly the party leader can change these settings."));
            return;
        }
        if (slot == 17) {
            openPartyMenu(player);
            return;
        }
        if (slot == 26) {
            startPartyDuel(party);
            return;
        }
        switch (slot) {
            case 0 -> {
                party.setKeepInventory(!party.isKeepInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 2 -> {
                party.setBlockArrows(!party.isBlockArrows());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 4 -> {
                party.setBlockFireworks(!party.isBlockFireworks());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 6 -> {
                party.setBlockPotions(!party.isBlockPotions());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case 8 -> {
                party.setBlockSlowFalling(!party.isBlockSlowFalling());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            default -> {
                return;
            }
        }
        openSettingsMenu(player);
    }


    public boolean startPartyDuel(Party party) {
        if (party == null || party.getMembers().size() < 2) {
            return false;
        }
        Player leader = Bukkit.getPlayer(party.getLeaderId());
        if (leader != null && !leader.isOp() && party.getMembers().size() < 2) {
            if (leader.isOnline()) {
                leader.sendMessage(configManager.format("§cAt least 2 players are required to start a party duel."));
            }
            return false;
        }
        activeSessions.entrySet().removeIf(entry -> entry.getValue() != null
                && entry.getValue().getParty() != null
                && entry.getValue().getParty().getId().equals(party.getId()));
        if (activeSessions.values().stream().anyMatch(session -> session.getParty().getId().equals(party.getId()))) {
            return false;
        }
        if (party.getArenaName() == null) {
            for (String arenaName : arenaManager.listArenas()) {
                if (!duelManager.isArenaInUse(arenaName)) {
                    party.setArenaName(arenaName);
                    break;
                }
            }
        }
        if (party.getArenaName() == null || duelManager.isArenaInUse(party.getArenaName())) {
            Player partyLeader = Bukkit.getPlayer(party.getLeaderId());
            if (partyLeader != null && partyLeader.isOnline()) {
                partyLeader.sendMessage(configManager.format("§cUnable to start party duel: the selected arena is already in use."));
            }
            return false;
        }
        TeamDuelSession session = new TeamDuelSession(party, duelManager);
        for (UUID member : party.getMembers()) {
            activeSessions.put(member, session);
            Player memberPlayer = Bukkit.getPlayer(member);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                memberPlayer.closeInventory();
                memberPlayer.sendTitle("§6Team Duel", "§7The battle is starting", 10, 30, 10);
                memberPlayer.playSound(memberPlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.1f);
            }
        }
        session.start();
        return true;
    }

    public TeamDuelSession getActiveSession(UUID playerId) {
        TeamDuelSession session = activeSessions.get(playerId);
        if (session != null && session.isEnded()) {
            return null;
        }
        return session;
    }

    public TeamDuelSession getAnyActiveSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public Collection<TeamDuelSession> getAllActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    public void removePartySession(TeamDuelSession session) {
        if (session == null) {
            return;
        }
        session.getParty().getMembers().forEach(activeSessions::remove);
    }

    public Party getParty(Player player) {
        return partiesByMember.get(player.getUniqueId());
    }

    public Party getParty(UUID playerId) {
        return partiesByMember.get(playerId);
    }

    private void registerParty(Party party) {
        partiesByLeader.put(party.getLeaderId(), party);
        registerPartyMember(party.getLeaderId(), party);
    }

    private void registerPartyMember(UUID playerId, Party party) {
        partiesByMember.put(playerId, party);
    }

    private void fillPane(Inventory inventory, Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createPane(material, " "));
            }
        }
    }


    private ItemStack createPlayerHead(Player player, String name, String lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setDisplayName(name);
        meta.setOwningPlayer(player);
        meta.setLore(List.of(lore));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createPane(Material material, String name, String... lore) {
        return createPremiumPane(material, name, false, lore);
    }

    private ItemStack createPremiumPane(Material material, String name, boolean glowing, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(List.of(lore));
        }
        if (glowing) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void openSmoothGui(Player player, Inventory inventory) {
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(duelManager.getPlugin(), () -> player.openInventory(inventory), 1L);
    }

    private ItemStack createToggleItem(Material material, String name, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + (enabled ? " §aON" : " §cOFF"));
        meta.setLore(List.of("§7Click to toggle"));
        item.setItemMeta(meta);
        return item;
    }

    private static class TargetComponent {
        private final Player inviter;
        private final Player target;
        private final PartyInvite invite;

        private TargetComponent(Player inviter, Player target, PartyInvite invite) {
            this.inviter = inviter;
            this.target = target;
            this.invite = invite;
        }

        private void send() {
            TextComponent header = new TextComponent("§6━━━━━━━━━━━━━━━━━━━━━━━━\n");
            TextComponent body = new TextComponent("§aParty request from §e" + inviter.getName() + "\n");
            TextComponent accept = new TextComponent("§a[Accept] ");
            accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + invite.getId()));
            accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Accept party invite").create()));
            TextComponent deny = new TextComponent("§c[Deny]");
            deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party deny " + invite.getId()));
            deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Deny party invite").create()));
            target.spigot().sendMessage(header, body, accept, deny);
        }
    }
}

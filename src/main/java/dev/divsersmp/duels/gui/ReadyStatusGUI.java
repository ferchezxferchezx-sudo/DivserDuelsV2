package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SkullType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class ReadyStatusGUI {
    private static final String GUI_ID = "ready_status";

    public static void open(Player player, dev.divsersmp.duels.Party party) {
        int members = party.getMembers().size();
        // Use 6 rows (54 slots) for bigger display with more spacing
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, "§c⚔ §fParty Ready Status §c⚔");

        // Create border filler (dark)
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bmeta = border.getItemMeta();
        if (bmeta != null) {
            bmeta.setDisplayName("§8");
            border.setItemMeta(bmeta);
        }

        // Prepare indicator panes - much more prominent
        ItemStack green = new ItemStack(Material.LIME_STAINED_GLASS);
        ItemMeta gmeta = green.getItemMeta();
        if (gmeta != null) {
            gmeta.setDisplayName("§a✓ READY");
            gmeta.setLore(List.of("§aConfirmed"));
            green.setItemMeta(gmeta);
        }

        ItemStack red = new ItemStack(Material.RED_STAINED_GLASS);
        ItemMeta rmeta = red.getItemMeta();
        if (rmeta != null) {
            rmeta.setDisplayName("§c✗ NOT READY");
            rmeta.setLore(List.of("§cWaiting..."));
            red.setItemMeta(rmeta);
        }

        // Fill border
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Convert Set to List for indexed access
        java.util.List<java.util.UUID> memberList = new java.util.ArrayList<>(party.getMembers());
        
        // Place heads in rows with spacious layout
        // Row 1 (slots 10-16): Players 1-4
        // Row 2 (slots 19-25): Status indicators for players 1-4
        // Row 3 (slots 28-34): Players 5-8
        // Row 4 (slots 37-43): Status indicators for players 5-8
        
        int index = 0;
        int[] playerSlots = {10, 12, 14, 16};     // Spacious layout for row 1
        int[] playerSlots2 = {28, 30, 32, 34};    // Spacious layout for row 3
        int[] statusSlots1 = {19, 21, 23, 25};    // Status for row 1 players
        int[] statusSlots2 = {37, 39, 41, 43};    // Status for row 2 players
        
        for (int i = 0; i < playerSlots.length && index < members; i++) {
            java.util.UUID memberId = memberList.get(index);
            Player member = Bukkit.getPlayer(memberId);
            ItemStack head;
            if (member != null) {
                head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(member);
                    List<String> lore = new ArrayList<>();
                    lore.add(party.isReady(memberId) ? "§a✓ Ready" : "§c✗ Not Ready");
                    sm.setLore(lore);
                    sm.setDisplayName("§f" + member.getName());
                    head.setItemMeta(sm);
                }
            } else {
                head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§fUnknown");
                    meta.setLore(List.of(party.isReady(memberId) ? "§a✓ Ready" : "§c✗ Not Ready"));
                    head.setItemMeta(meta);
                }
            }
            inv.setItem(playerSlots[i], head);
            inv.setItem(statusSlots1[i], party.isReady(memberId) ? green : red);
            index++;
        }
        
        for (int i = 0; i < playerSlots2.length && index < members; i++) {
            java.util.UUID memberId = memberList.get(index);
            Player member = Bukkit.getPlayer(memberId);
            ItemStack head;
            if (member != null) {
                head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(member);
                    List<String> lore = new ArrayList<>();
                    lore.add(party.isReady(memberId) ? "§a✓ Ready" : "§c✗ Not Ready");
                    sm.setLore(lore);
                    sm.setDisplayName("§f" + member.getName());
                    head.setItemMeta(sm);
                }
            } else {
                head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§fUnknown");
                    meta.setLore(List.of(party.isReady(memberId) ? "§a✓ Ready" : "§c✗ Not Ready"));
                    head.setItemMeta(meta);
                }
            }
            inv.setItem(playerSlots2[i], head);
            inv.setItem(statusSlots2[i], party.isReady(memberId) ? green : red);
            index++;
        }

        // Close button at bottom-right
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§cClose");
            closeMeta.setLore(List.of("§7Close the status menu. You remain queued."));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(52, close);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

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
        int size = 9;
        int members = party.getMembers().size();
        while (size < members + 2) size += 9;

        Inventory inv = Bukkit.createInventory(null, size, "Party Ready Status");

        int slot = 0;
        for (java.util.UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            ItemStack head;
            if (member != null) {
                head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(member);
                    List<String> lore = new ArrayList<>();
                    if (party.isReady(memberId)) {
                        lore.add("§aReady");
                    } else {
                        lore.add("§7Not ready");
                    }
                    sm.setLore(lore);
                    sm.setDisplayName("§f" + (member != null ? member.getName() : "Unknown"));
                    head.setItemMeta(sm);
                }
            } else {
                head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§fUnknown");
                    meta.setLore(List.of(party.isReady(memberId) ? "§aReady" : "§7Not ready"));
                    head.setItemMeta(meta);
                }
            }
            inv.setItem(slot++, head);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§cClose");
            closeMeta.setLore(List.of("§7Close the status menu. You remain queued."));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(size - 2, close);

        // filler
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta fmeta = filler.getItemMeta();
        if (fmeta != null) {
            fmeta.setDisplayName("§8");
            filler.setItemMeta(fmeta);
        }
        for (int i = slot; i < size - 2; i++) {
            inv.setItem(i, filler);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

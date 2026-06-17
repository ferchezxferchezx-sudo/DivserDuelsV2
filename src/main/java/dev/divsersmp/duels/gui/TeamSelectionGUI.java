package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class TeamSelectionGUI {
    private static final String GUI_ID = "team_selection";

    public static void open(Player leader, dev.divsersmp.duels.Party party) {
        Inventory inv = Bukkit.createInventory(null, 27, "§dTeam Selection");
        // Red line: top row (0-8) for not-ready / red team slots
        ItemStack redGlass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta rmeta = redGlass.getItemMeta(); if (rmeta != null) { rmeta.setDisplayName("Red Team"); redGlass.setItemMeta(rmeta); }
        for (int i = 0; i <= 8; i++) inv.setItem(i, redGlass);

        // Middle row controls: barrier (back) at 9, random horn at 13, next (iron sword) at 17
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bmeta = back.getItemMeta(); if (bmeta != null) { bmeta.setDisplayName("Back"); back.setItemMeta(bmeta); }
        inv.setItem(9, back);

        ItemStack horn = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta hmeta = horn.getItemMeta(); if (hmeta != null) { hmeta.setDisplayName("Randomize Teams"); hmeta.setLore(java.util.List.of("Click to randomly assign teams")); horn.setItemMeta(hmeta); }
        inv.setItem(13, horn);

        ItemStack next = new ItemStack(Material.IRON_SWORD);
        ItemMeta nmeta = next.getItemMeta(); if (nmeta != null) { nmeta.setDisplayName("Next"); next.setItemMeta(nmeta); }
        inv.setItem(17, next);

        // Lime line: bottom row (18-26)
        ItemStack limeGlass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta lm = limeGlass.getItemMeta(); if (lm != null) { lm.setDisplayName("Lime Team"); limeGlass.setItemMeta(lm); }
        for (int i = 18; i <= 26; i++) inv.setItem(i, limeGlass);

        // Place player heads into top (red) or bottom (lime) rows according to party assignments
        if (party != null) {
            int redSlot = 0;
            int limeSlot = 18;
            for (java.util.UUID member : party.getMembers()) {
                Player p = Bukkit.getPlayer(member);
                if (p == null || !p.isOnline()) continue;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(p);
                    sm.setDisplayName(p.getName());
                    head.setItemMeta(sm);
                }
                if ("lime".equalsIgnoreCase(party.getTeam(member))) {
                    if (limeSlot <= 26) inv.setItem(limeSlot++, head);
                } else {
                    if (redSlot <= 8) inv.setItem(redSlot++, head);
                }
            }
        }

        // Fill empty with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fmeta = filler.getItemMeta(); if (fmeta != null) { fmeta.setDisplayName(" "); filler.setItemMeta(fmeta); }
        for (int i = 0; i < 27; i++) if (inv.getItem(i) == null) inv.setItem(i, filler);

        leader.openInventory(inv);
        leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        leader.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

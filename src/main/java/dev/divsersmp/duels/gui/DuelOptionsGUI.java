package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class DuelOptionsGUI {
    private static final String GUI_ID = "duel_options";

    public static void open(Player player, dev.divsersmp.duels.Party party) {
        Inventory inv = Bukkit.createInventory(null, 27, "§bDuel Options");
        // Minimal layout: row 1 items at slots 2-6, dyes under them at 11-15
        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fm = firework.getItemMeta(); if (fm != null) { fm.setDisplayName("Fireworks"); fm.setLore(Arrays.asList("Click the dye beneath to toggle")); firework.setItemMeta(fm); }
        inv.setItem(2, firework);

        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta pm = potion.getItemMeta(); if (pm != null) { pm.setDisplayName("Potions"); pm.setLore(Arrays.asList("Click the dye beneath to toggle")); potion.setItemMeta(pm); }
        inv.setItem(3, potion);

        ItemStack arrows = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta am = arrows.getItemMeta(); if (am != null) { am.setDisplayName("Arrows"); am.setLore(Arrays.asList("Click the dye beneath to toggle")); arrows.setItemMeta(am); }
        inv.setItem(4, arrows);

        ItemStack slow = new ItemStack(Material.SPLASH_POTION);
        ItemMeta sm = slow.getItemMeta(); if (sm != null) { sm.setDisplayName("Slow Falling Potion"); sm.setLore(Arrays.asList("Click the dye beneath to toggle")); slow.setItemMeta(sm); }
        inv.setItem(5, slow);

        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta mac = mace.getItemMeta(); if (mac != null) { mac.setDisplayName("§6Mace"); mac.setLore(Arrays.asList("§7Click the dye beneath to toggle")); mace.setItemMeta(mac); }
        inv.setItem(6, mace);

        // Dyes under each option (slots 11-15): lime when allowed, gray when blocked
        ItemStack dyeLime = new ItemStack(Material.LIME_DYE);
        ItemMeta ld = dyeLime.getItemMeta(); if (ld != null) { ld.setDisplayName("§aAllowed"); dyeLime.setItemMeta(ld); }
        ItemStack dyeGray = new ItemStack(Material.GRAY_DYE);
        ItemMeta gd = dyeGray.getItemMeta(); if (gd != null) { gd.setDisplayName("§7Blocked"); dyeGray.setItemMeta(gd); }

        inv.setItem(11, party != null && party.isBlockFireworks() ? new ItemStack(dyeGray) : new ItemStack(dyeLime));
        inv.setItem(12, party != null && party.isBlockPotions() ? new ItemStack(dyeGray) : new ItemStack(dyeLime));
        inv.setItem(13, party != null && party.isBlockArrows() ? new ItemStack(dyeGray) : new ItemStack(dyeLime));
        inv.setItem(14, party != null && party.isBlockSlowFalling() ? new ItemStack(dyeGray) : new ItemStack(dyeLime));
        inv.setItem(15, party != null && party.isBlockMace() ? new ItemStack(dyeGray) : new ItemStack(dyeLime));

        // Navigation: barrier at slot 9 (index 9), next iron sword at slot 17
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bmeta = back.getItemMeta(); if (bmeta != null) { bmeta.setDisplayName("Back"); back.setItemMeta(bmeta); }
        inv.setItem(9, back);

        ItemStack next = new ItemStack(Material.IRON_SWORD);
        ItemMeta nmeta = next.getItemMeta(); if (nmeta != null) { nmeta.setDisplayName("Ready"); next.setItemMeta(nmeta); }
        inv.setItem(17, next);

        // fill rest with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fmeta = filler.getItemMeta(); if (fmeta != null) { fmeta.setDisplayName(" "); filler.setItemMeta(fmeta); }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() { return GUI_ID; }
}

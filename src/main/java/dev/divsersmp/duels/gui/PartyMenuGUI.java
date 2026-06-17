package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import java.util.Arrays;

public class PartyMenuGUI {
    private static final String GUI_ID = "party_menu";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Party Selection");

        ItemStack ironSword = new ItemStack(Material.IRON_SWORD);
        ItemMeta swordMeta = ironSword.getItemMeta();
        if (swordMeta != null) {
            swordMeta.setDisplayName("§a1 vs 1 Duels");
            swordMeta.setLore(Arrays.asList("§7Click to start a classic duel", "§7Test your skills one-on-one"));
            ironSword.setItemMeta(swordMeta);
        }
        inv.setItem(11, ironSword);

        ItemStack blueChest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta lam = (LeatherArmorMeta) blueChest.getItemMeta();
        if (lam != null) {
            lam.setDisplayName("§9Team Duels");
            lam.setLore(Arrays.asList("§7Click to create a team party", "§7Battle with your squad"));
            lam.setColor(Color.fromRGB(85, 141, 255));
            blueChest.setItemMeta(lam);
        }
        inv.setItem(15, blueChest);

        ItemStack chain = new ItemStack(Material.CHAIN);
        ItemMeta chainMeta = chain.getItemMeta();
        if (chainMeta != null) {
            chainMeta.setDisplayName("§8Decoration");
            chain.setItemMeta(chainMeta);
        }
        inv.setItem(10, chain);
        inv.setItem(12, chain);
        inv.setItem(14, chain);
        inv.setItem(16, chain);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName("§7");
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, border);
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

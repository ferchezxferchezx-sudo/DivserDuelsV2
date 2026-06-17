package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class KeepInventoryGUI {
    private static final String GUI_ID = "keep_inventory";

    public static void open(Player player, dev.divsersmp.duels.Party party) {
        Inventory inv = Bukkit.createInventory(null, 27, "§eKeep Inventory Setting");

        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setDisplayName("§6Keep Inventory");
            chestMeta.setLore(Arrays.asList("§7Choose whether items stay after death", "§7This rule applies during the duel."));
            chest.setItemMeta(chestMeta);
        }
        inv.setItem(13, chest);

        ItemStack chain = new ItemStack(Material.CHAIN);
        ItemMeta chainMeta = chain.getItemMeta();
        if (chainMeta != null) {
            chainMeta.setDisplayName("§7");
            chain.setItemMeta(chainMeta);
        }
        inv.setItem(0, chain);
        inv.setItem(8, chain);
        inv.setItem(9, chain);
        inv.setItem(17, chain);

        ItemStack redWool = new ItemStack(Material.RED_WOOL);
        ItemMeta redMeta = redWool.getItemMeta();
        if (redMeta != null) {
            redMeta.setDisplayName("§cKeep Inventory: OFF");
            redMeta.setLore(Arrays.asList("§7Disabled", "§7Items will not be kept after death"));
            redWool.setItemMeta(redMeta);
        }
        inv.setItem(10, redWool);

        ItemStack greenWool = new ItemStack(Material.GREEN_WOOL);
        ItemMeta greenMeta = greenWool.getItemMeta();
        if (greenMeta != null) {
            greenMeta.setDisplayName("§aKeep Inventory: ON");
            greenMeta.setLore(Arrays.asList("§7Enabled", "§7Items will be kept after death"));
            greenWool.setItemMeta(greenMeta);
        }
        inv.setItem(16, greenWool);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cBack");
            backMeta.setLore(Arrays.asList("§7Return to team selection", "§7Keep your current party flow."));
            back.setItemMeta(backMeta);
        }
        inv.setItem(18, back);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName("§8");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
        // no title to reduce HUD spam
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

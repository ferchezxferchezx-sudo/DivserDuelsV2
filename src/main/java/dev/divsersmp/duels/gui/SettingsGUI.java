package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class SettingsGUI {
    private static final String GUI_ID = "settings";

    public static void open(Player player, boolean keepInventory) {
        Inventory inv = Bukkit.createInventory(null, 27, "§5Team Duel Settings");

        ItemStack keepInvDisplay = keepInventory ? new ItemStack(Material.GREEN_WOOL) : new ItemStack(Material.RED_WOOL);
        ItemMeta displayMeta = keepInvDisplay.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(keepInventory ? "§aKeep Inventory: ON" : "§cKeep Inventory: OFF");
            displayMeta.setLore(Arrays.asList("§7Current inventory mode", "§7Will apply to the duel"));
            keepInvDisplay.setItemMeta(displayMeta);
        }
        inv.setItem(13, keepInvDisplay);

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

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName("§cBack");
            barrierMeta.setLore(Arrays.asList("§7Return to the keep inventory menu", "§7Adjust the party rules before starting the duel"));
            barrier.setItemMeta(barrierMeta);
        }
        inv.setItem(18, barrier);

        ItemStack ready = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta readyMeta = ready.getItemMeta();
        if (readyMeta != null) {
            readyMeta.setDisplayName("§6Start Duel");
            readyMeta.setLore(Arrays.asList("§7Start the team duel immediately", "§8Leader only"));
            ready.setItemMeta(readyMeta);
        }
        inv.setItem(26, ready);

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
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
        player.sendTitle("§5Team Duel Settings", "§7Review settings and start the duel", 10, 60, 10);
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

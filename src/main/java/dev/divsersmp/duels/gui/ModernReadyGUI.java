package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class ModernReadyGUI {
    private static final String GUI_ID = "ready";

    public static void open(Player player, dev.divsersmp.duels.Party party) {
        Inventory inv = Bukkit.createInventory(null, 27, "Ready");

        setSlot(inv, 0, Material.JUNGLE_LOG);
        setSlot(inv, 1, Material.JUNGLE_LEAVES);
        setSlot(inv, 2, Material.JUNGLE_LEAVES);
        setSlot(inv, 3, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 4, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 5, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 6, Material.JUNGLE_LEAVES);
        setSlot(inv, 7, Material.JUNGLE_LEAVES);
        setSlot(inv, 8, Material.JUNGLE_LOG);

        setSlot(inv, 9, Material.JUNGLE_LOG);
        setSlot(inv, 10, Material.JUNGLE_LEAVES);
        setSlot(inv, 11, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 12, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        ItemStack ready = new ItemStack(Material.EMERALD);
        ItemMeta readyMeta = ready.getItemMeta();
        if (readyMeta != null) {
            readyMeta.setDisplayName("§aReady");
            if (party != null && party.isReady(player.getUniqueId())) {
                readyMeta.setLore(Arrays.asList("§aQueued for the duel", "§7Waiting for the rest of the party"));
            } else {
                readyMeta.setLore(Arrays.asList("§7Click to queue for the duel."));
            }
            ready.setItemMeta(readyMeta);
        }
        inv.setItem(13, ready);

        setSlot(inv, 14, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 15, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        setSlot(inv, 16, Material.JUNGLE_LEAVES);
        setSlot(inv, 17, Material.JUNGLE_LOG);
        setSlot(inv, 18, Material.JUNGLE_LOG);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§cClose");
            cancelMeta.setLore(Arrays.asList("§7Close the ready menu."));
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(19, cancel);

        Material grassItem = getGrassItem();
        setSlot(inv, 20, grassItem);
        setSlot(inv, 21, grassItem);
        setSlot(inv, 22, grassItem);
        setSlot(inv, 23, grassItem);
        setSlot(inv, 24, grassItem);
        setSlot(inv, 25, grassItem);
        setSlot(inv, 26, Material.JUNGLE_LOG);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    private static void setSlot(Inventory inventory, int slot, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8");
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public static String getGuiId() {
        return GUI_ID;
    }

    private static Material getGrassItem() {
        try {
            return Material.valueOf("GRASS");
        } catch (IllegalArgumentException ex) {
            return Material.GRASS_BLOCK;
        }
    }

    private static String blockedList(dev.divsersmp.duels.Party party) {
        java.util.List<String> blocked = new java.util.ArrayList<>();
        if (party.isBlockFireworks()) blocked.add("Fireworks");
        if (party.isBlockPotions()) blocked.add("Potions");
        if (party.isBlockArrows()) blocked.add("Arrows");
        if (party.isBlockSlowFalling()) blocked.add("SlowFall");
        if (party.isBlockMace()) blocked.add("Mace");
        return blocked.isEmpty() ? "none" : String.join(", ", blocked);
    }
}

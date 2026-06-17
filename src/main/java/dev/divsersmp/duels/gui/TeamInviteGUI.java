package dev.divsersmp.duels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.Arrays;
import java.util.List;

public class TeamInviteGUI {
    private static final String GUI_ID = "team_invite";

    private static final List<Integer> INVITE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );

    public static void open(Player leader) {
        Inventory inv = Bukkit.createInventory(null, 27, "§9Team Members - Max 14");

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cBack");
            backMeta.setLore(Arrays.asList("§7Return to the party menu"));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(18, backButton);

        ItemStack nextButton = new ItemStack(Material.IRON_SWORD);
        ItemMeta nextMeta = nextButton.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName("§aNext: Team Selection");
            nextMeta.setLore(Arrays.asList("§7Proceed to team selection"));
            nextButton.setItemMeta(nextMeta);
        }
        inv.setItem(26, nextButton);

        // Decorative glass panes in the upper middle
        ItemStack decor = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta decorMeta = decor.getItemMeta();
        if (decorMeta != null) { decorMeta.setDisplayName(" "); decor.setItemMeta(decorMeta); }
        inv.setItem(3, decor);
        inv.setItem(4, decor);
        inv.setItem(5, decor);

        int headIndex = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(leader.getUniqueId())) {
                continue;
            }
            if (headIndex >= INVITE_SLOTS.size()) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(online);
                skullMeta.setDisplayName("§a" + online.getName());
                skullMeta.setLore(Arrays.asList("§7Click to invite"));
                head.setItemMeta(skullMeta);
            }
            inv.setItem(INVITE_SLOTS.get(headIndex), head);
            headIndex++;
        }

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

        leader.openInventory(inv);
        leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        leader.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
            Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
        // no title to avoid HUD spam
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

package dev.divsersmp.duels.gui;

import dev.divsersmp.duels.Party;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PartyMemberSelectionGUI {
    private static final String GUI_ID = "party_member_selection";
    private static final List<Integer> MEMBER_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );

    public static void open(Player leader, Party party) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Select Party Member");

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cBack");
            backMeta.setLore(Arrays.asList("§7Return to the party menu"));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(0, backButton);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName("§7");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 1; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        if (party != null) {
            List<UUID> memberIds = new ArrayList<>(party.getMembers());
            memberIds.remove(leader.getUniqueId());
            int index = 0;
            for (UUID memberId : memberIds) {
                if (index >= MEMBER_SLOTS.size()) break;
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !member.isOnline()) continue;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                if (skullMeta != null) {
                    skullMeta.setOwningPlayer(member);
                    skullMeta.setDisplayName("§a" + member.getName());
                    skullMeta.setLore(Arrays.asList("§7Click to challenge this player"));
                    head.setItemMeta(skullMeta);
                }
                inv.setItem(MEMBER_SLOTS.get(index), head);
                index++;
            }
        }

        leader.openInventory(inv);
        leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        leader.setMetadata("gui_type", new org.bukkit.metadata.FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("DivserDuelsXYZ"), GUI_ID));
    }

    public static String getGuiId() {
        return GUI_ID;
    }
}

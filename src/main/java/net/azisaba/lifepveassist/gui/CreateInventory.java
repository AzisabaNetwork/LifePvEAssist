package net.azisaba.lifepveassist.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CreateInventory {

    public CreateInventory() {}

    public void step1(Player player) {
        Inventory inv = Bukkit.createInventory(new LifeCraftHolder(), 27, ChatColor.translateAlternateColorCodes('&', "&b&l圧縮ダイヤ&f,&7&l圧縮鉄&f,&6&l圧縮金&fのブロックを入れてください"));
        for(int i = 0; i <= 9; ++i) {
            inv.setItem(i, (new CreateItem()).getBackPane());
        }
        for(int i = 17; i <= 25; ++i) {
            inv.setItem(i, (new CreateItem()).getBackPane());
        }
        inv.setItem(26, (new CreateItem()).getNextStep2());
        player.openInventory(inv);
    }

    public void step2(Player player) {
        Inventory inv = Bukkit.createInventory(new CraftHolder(), 54, ChatColor.translateAlternateColorCodes('&', "&f[LifeCraft]と書かれた説明付きのアイテムを入れてください"));
        for (int i = 0; i <= 9; ++i) {
            inv.setItem(i, (new CreateItem()).getBackPane());
        }
        inv.setItem(17, (new CreateItem()).getBackPane());
        inv.setItem(18, (new CreateItem()).getBackPane());
        inv.setItem(26, (new CreateItem()).getBackPane());
        inv.setItem(27, (new CreateItem()).getBackPane());
        inv.setItem(35, (new CreateItem()).getBackPane());
        inv.setItem(36, (new CreateItem()).getBackPane());
        for (int i = 44; i <= 53; ++i) {
            inv.setItem(i, (new CreateItem()).getBackPane());
        }
        player.openInventory(inv);
    }
}

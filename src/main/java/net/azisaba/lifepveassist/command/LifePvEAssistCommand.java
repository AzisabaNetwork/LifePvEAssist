package net.azisaba.lifepveassist.command;

import net.azisaba.lifepveassist.gui.CreateInventory;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class LifePvEAssistCommand implements CommandExecutor {

    public LifePvEAssistCommand() {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("lifepveassist.command.lifecraft")) {
                player.sendMessage(ChatColor.RED + "権限がありません。");
                return true;
            }
            if (args.length == 0) {
                new CreateInventory().step1(player);
                player.sendMessage(ChatColor.GREEN + "LifeCraftメニューを開きました。");
            }
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "コンソールからは実行できません。");
            return true;
        }
    }
}

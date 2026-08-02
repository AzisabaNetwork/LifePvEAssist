package net.azisaba.lifepveassist.command;

import net.azisaba.lifepveassist.gui.CreateInventory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LifePvEAssistCommand implements CommandExecutor {

    public LifePvEAssistCommand() {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission("lifepveassist.command.lifecraft")) {
                player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                return true;
            }
            if (args.length == 0) {
                new CreateInventory().step1(player);
                player.sendMessage(Component.text("LifeCraftメニューを開きました。", NamedTextColor.GREEN));
            }
            return true;
        } else {
            sender.sendMessage(Component.text("コンソールからは実行できません。", NamedTextColor.RED));
            return true;
        }
    }
}

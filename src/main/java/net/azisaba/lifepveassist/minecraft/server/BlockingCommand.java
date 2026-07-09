package net.azisaba.lifepveassist.minecraft.server;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlockingCommand implements CommandExecutor {

    public BlockingCommand() {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            java.util.UUID uuid = player.getUniqueId();

            if (args.length > 0 && args[0].equalsIgnoreCase("notify")) {
                if (PacketListenerImpl.hideNotification.contains(uuid)) {
                    PacketListenerImpl.hideNotification.remove(uuid);
                    player.sendMessage(ChatColor.GREEN + "ブロック時の通知をオンにしました。");
                } else {
                    PacketListenerImpl.hideNotification.add(uuid);
                    player.sendMessage(ChatColor.GREEN + "ブロック時の通知をオフにしました。");
                }
                return true;
            }

            if (!PacketListenerImpl.map.isEmpty() && PacketListenerImpl.map.containsKey(uuid)) {
                PacketListenerImpl.map.remove(uuid);
                player.sendMessage(ChatColor.GREEN + "リペア、サルベージをブロックしました。");
            } else {
                PacketListenerImpl.map.put(uuid, true);
                player.sendMessage(ChatColor.GREEN + "リペア、サルベージのブロックを解除しました。");
            }
        } else {
            sender.sendMessage("コンソールからは使用できません。");
        }
        return true;
    }
}

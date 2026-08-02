package net.azisaba.lifepveassist.minecraft.server;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlockingCommand implements CommandExecutor {

    public BlockingCommand() {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            java.util.UUID uuid = player.getUniqueId();

            if (args.length > 0 && args[0].equalsIgnoreCase("notify")) {
                if (PacketListenerImpl.hideNotification.contains(uuid)) {
                    PacketListenerImpl.hideNotification.remove(uuid);
                    player.sendMessage(Component.text("ブロック時の通知をオンにしました。", NamedTextColor.GREEN));
                } else {
                    PacketListenerImpl.hideNotification.add(uuid);
                    player.sendMessage(Component.text("ブロック時の通知をオフにしました。", NamedTextColor.GREEN));
                }
                return true;
            }

            if (!PacketListenerImpl.map.isEmpty() && PacketListenerImpl.map.containsKey(uuid)) {
                PacketListenerImpl.map.remove(uuid);
                player.sendMessage(Component.text("リペア、サルベージをブロックしました。", NamedTextColor.GREEN));
            } else {
                PacketListenerImpl.map.put(uuid, true);
                player.sendMessage(Component.text("リペア、サルベージのブロックを解除しました。", NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text("コンソールからは使用できません。", NamedTextColor.RED));
        }
        return true;
    }
}

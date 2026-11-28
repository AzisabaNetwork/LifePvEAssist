package net.azisaba.lifepveassist.command;

import net.azisaba.lifepveassist.gui.CreateInventory;
import net.azisaba.lifepveassist.temperature.TemperatureFeature;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LifePvEAssistCommand implements CommandExecutor, TabCompleter {

    private final TemperatureFeature temperatureFeature;

    public LifePvEAssistCommand(TemperatureFeature temperatureFeature) {
        this.temperatureFeature = temperatureFeature;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "temp".equalsIgnoreCase(args[0])) {
            if (temperatureFeature == null || !temperatureFeature.isActive()
                    || temperatureFeature.getSubCommand() == null) {
                sender.sendMessage(Component.text("現在このサーバーでは温度管理機能が無効になっています。", NamedTextColor.RED));
                return true;
            }
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return temperatureFeature.getSubCommand().execute(sender, label, subArgs);
        }

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
            sender.sendMessage(Component.text("コンソールからは /" + label + " temp <...> のみ実行できます。", NamedTextColor.RED));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if ("temp".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                return List.of("temp");
            }
            return List.of();
        }
        if (args.length >= 2 && "temp".equalsIgnoreCase(args[0])
                && temperatureFeature != null && temperatureFeature.isActive()
                && temperatureFeature.getSubCommand() != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return temperatureFeature.getSubCommand().tabComplete(sender, subArgs);
        }
        return List.of();
    }
}

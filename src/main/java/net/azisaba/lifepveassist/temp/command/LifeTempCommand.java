package net.azisaba.lifepveassist.temp.command;

import net.azisaba.lifepveassist.temp.armor.ArmorInspector;
import net.azisaba.lifepveassist.temp.config.ConfigManager;
import net.azisaba.lifepveassist.temp.core.TemperatureManager;
import net.azisaba.lifepveassist.temp.model.ArmorResistance;
import net.azisaba.lifepveassist.temp.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temp.ui.BossBarController;
import net.azisaba.lifepveassist.temp.zone.TemperatureZone;
import net.azisaba.lifepveassist.temp.zone.WorldEditSelectionProvider;
import net.azisaba.lifepveassist.temp.zone.ZoneManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /lifetemp (/ltm, /temp) コマンドおよびタブ補完の実装。
 */
public class LifeTempCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold>[LifeTemp] <reset>";

    private final ConfigManager config;
    private final TemperatureManager temperatureManager;
    private final ArmorInspector armorInspector;
    private final ZoneManager zoneManager;
    private final Runnable reloadAction;

    public LifeTempCommand(
            ConfigManager config,
            TemperatureManager temperatureManager,
            ArmorInspector armorInspector,
            ZoneManager zoneManager,
            Runnable reloadAction
    ) {
        this.config = config;
        this.temperatureManager = temperatureManager;
        this.armorInspector = armorInspector;
        this.zoneManager = zoneManager;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> handleInfo(sender, args);
            case "set" -> handleSet(sender, args);
            case "add" -> handleAdd(sender, args);
            case "reset" -> handleReset(sender, args);
            case "armor" -> handleArmor(sender, args);
            case "zone" -> handleZone(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "<red>プレイヤーが見つかりません: " + args[1]);
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            msg(sender, "<red>コンソールからは対象プレイヤーを指定してください: /ltm info <player>");
            return;
        }

        ArmorResistance res = armorInspector.inspectPlayer(target);
        TemperatureEvaluation eval = temperatureManager.evaluateCurrent(target);
        boolean activeWorld = config.isEnabledWorld(target.getWorld());

        msg(sender, "<yellow>=== " + target.getName() + " の温度情報 ===");
        msg(sender, "<gray>現在ワールド: <white>" + target.getWorld().getName()
                + (activeWorld ? " <green>(有効)" : " <dark_gray>(対象外)"));
        msg(sender, "<gray>現在温度: <white>" + BossBarController.formatTemp(eval.temperature()) + "℃"
                + " <gray>| 状態: <white>" + eval.severity().name());
        msg(sender, "<gray>即死ライン: <aqua>" + BossBarController.formatTemp(eval.minLimit()) + "℃"
                + " <gray>～ <red>" + BossBarController.formatTemp(eval.maxLimit()) + "℃");
        msg(sender, "<gray>耐熱防具: <red>" + res.heatPieces() + "部位 <gray>(上限 +"
                + BossBarController.formatTemp(res.heatLimitBonus()) + "℃)");
        msg(sender, "<gray>耐冷防具: <aqua>" + res.coldPieces() + "部位 <gray>(下限 -"
                + BossBarController.formatTemp(res.coldLimitBonus()) + "℃)");
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "<red>使用法: /ltm set <player> <温度>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>プレイヤーが見つかりません: " + args[1]);
            return;
        }
        Double value = parseDouble(args[2]);
        if (value == null) {
            msg(sender, "<red>有効な数値を指定してください: " + args[2]);
            return;
        }
        temperatureManager.setTemperature(target, value, true);
        msg(sender, "<green>" + target.getName() + " の温度を <white>"
                + BossBarController.formatTemp(value) + "℃ <green>に設定しました。");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "<red>使用法: /ltm add <player> <変化量>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>プレイヤーが見つかりません: " + args[1]);
            return;
        }
        Double delta = parseDouble(args[2]);
        if (delta == null) {
            msg(sender, "<red>有効な数値を指定してください: " + args[2]);
            return;
        }
        if (!config.isEnabledWorld(target.getWorld())) {
            // 管理コマンドでは対象外ワールドでも強制変更できるよう set 経由で反映
            double next = temperatureManager.getTemperature(target) + delta;
            temperatureManager.setTemperature(target, next, true);
        } else {
            temperatureManager.addTemperature(target, delta, true);
        }
        msg(sender, "<green>" + target.getName() + " の温度を <white>"
                + (delta >= 0 ? "+" : "") + BossBarController.formatTemp(delta)
                + "℃ <green>変更しました (現在: <white>"
                + BossBarController.formatTemp(temperatureManager.getTemperature(target)) + "℃<green>)。");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "<red>使用法: /ltm reset <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>プレイヤーが見つかりません: " + args[1]);
            return;
        }
        temperatureManager.resetTemperature(target);
        msg(sender, "<green>" + target.getName() + " の温度をデフォルト ("
                + BossBarController.formatTemp(config.getDefaultTemp()) + "℃) にリセットしました。");
    }

    private void handleArmor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
            return;
        }
        if (args.length < 2) {
            msg(sender, "<red>使用法: /ltm armor <set|clear|inspect>");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg(sender, "<red>メインハンドに対象の防具アイテムを持ってください。");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    msg(sender, "<red>使用法: /ltm armor set <heat|cold> <延長温度> [部位数(既定:1)]");
                    return;
                }
                String type = args[2].toLowerCase(Locale.ROOT);
                boolean isHeat;
                if ("heat".equals(type)) {
                    isHeat = true;
                } else if ("cold".equals(type)) {
                    isHeat = false;
                } else {
                    msg(sender, "<red>タイプは heat または cold を指定してください。");
                    return;
                }
                Double bonus = parseDouble(args[3]);
                if (bonus == null || bonus < 0.0) {
                    msg(sender, "<red>延長温度は0以上の数値で指定してください。");
                    return;
                }
                int pieces = 1;
                if (args.length >= 5) {
                    Integer p = parseInt(args[4]);
                    if (p == null || p < 0) {
                        msg(sender, "<red>部位数は0以上の整数で指定してください。");
                        return;
                    }
                    pieces = p;
                }
                armorInspector.applyResistance(hand, isHeat, bonus, pieces);
                msg(sender, "<green>手持ちアイテムに " + (isHeat ? "<red>耐熱" : "<aqua>耐冷")
                        + " <white>+" + BossBarController.formatTemp(bonus) + "℃ (" + pieces + "部位) <green>を付与しました。");
            }
            case "clear" -> {
                armorInspector.clearResistance(hand);
                msg(sender, "<green>手持ちアイテムの PDC 温度耐性タグを削除しました。");
            }
            case "inspect" -> {
                ArmorInspector.ItemInspectionResult result = armorInspector.inspectItem(hand);
                ArmorResistance r = result.resistance();
                msg(sender, "<yellow>=== アイテム耐性情報 ===");
                msg(sender, "<gray>検出元: <white>" + result.source());
                msg(sender, "<gray>耐熱: <red>+" + BossBarController.formatTemp(r.heatLimitBonus())
                        + "℃ (" + r.heatPieces() + "部位)");
                msg(sender, "<gray>耐冷: <aqua>-" + BossBarController.formatTemp(r.coldLimitBonus())
                        + "℃ (" + r.coldPieces() + "部位)");
            }
            default -> msg(sender, "<red>使用法: /ltm armor <set|clear|inspect>");
        }
    }

    private void handleZone(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "<red>使用法: /ltm zone <create|delete|list|info|show|hide|tp>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
                    return;
                }
                if (args.length < 5) {
                    msg(sender, "<red>使用法: /ltm zone create <エリア名> <変化量(例:5 / -5)> <間隔秒(例:10)>");
                    msg(sender, "<gray>※先に WorldEdit (//wand, //pos1, //pos2 等) で範囲を選択してください。");
                    return;
                }
                String name = args[2];
                Double delta = parseDouble(args[3]);
                Integer interval = parseInt(args[4]);
                if (delta == null) {
                    msg(sender, "<red>変化量は数値で指定してください。");
                    return;
                }
                if (interval == null || interval < 1) {
                    msg(sender, "<red>間隔秒は1以上の整数で指定してください。");
                    return;
                }
                WorldEditSelectionProvider.SelectionResult result = WorldEditSelectionProvider.getSelection(player);
                if (!result.isSuccess()) {
                    msg(sender, "<red>" + result.errorMessage());
                    return;
                }
                TemperatureZone zone = result.bounds().toZone(name, delta, interval);
                zoneManager.addZone(zone);
                msg(sender, "<green>温度エリア <yellow>" + zone.name() + " <green>を登録しました！ "
                        + "<gray>(" + zone.worldName() + " / " + interval + "秒ごとに "
                        + (delta >= 0 ? "+" : "") + BossBarController.formatTemp(delta) + "℃ / "
                        + zone.volume() + "ブロック)");
                zoneManager.showZonePreview(player, zone);
            }
            case "delete", "remove" -> {
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /ltm zone delete <エリア名>");
                    return;
                }
                if (zoneManager.removeZone(args[2])) {
                    msg(sender, "<green>温度エリア <yellow>" + args[2] + " <green>を削除しました。");
                } else {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                }
            }
            case "list" -> {
                if (zoneManager.getZones().isEmpty()) {
                    msg(sender, "<gray>登録されている温度エリアはありません。");
                    return;
                }
                msg(sender, "<yellow>=== 登録済み温度エリア一覧 (" + zoneManager.getZones().size() + "件) ===");
                for (TemperatureZone z : zoneManager.getZones()) {
                    String sign = z.delta() >= 0 ? "<red>+" : "<aqua>";
                    msg(sender, "<white>- <yellow>" + z.name() + " <gray>[" + z.worldName() + "] "
                            + sign + BossBarController.formatTemp(z.delta()) + "℃ <gray>/ " + z.intervalSeconds() + "秒");
                }
            }
            case "info" -> {
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /ltm zone info <エリア名>");
                    return;
                }
                TemperatureZone z = zoneManager.getZone(args[2]);
                if (z == null) {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                    return;
                }
                msg(sender, "<yellow>=== エリア情報: " + z.name() + " ===");
                msg(sender, "<gray>ワールド: <white>" + z.worldName());
                msg(sender, "<gray>範囲: <white>(" + z.minX() + ", " + z.minY() + ", " + z.minZ()
                        + ") ～ (" + z.maxX() + ", " + z.maxY() + ", " + z.maxZ() + ")");
                msg(sender, "<gray>体積: <white>" + z.volume() + " ブロック");
                msg(sender, "<gray>温度変化: <white>" + (z.delta() >= 0 ? "+" : "")
                        + BossBarController.formatTemp(z.delta()) + "℃ <gray>/ " + z.intervalSeconds() + "秒ごと");
            }
            case "show" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /ltm zone show <エリア名>");
                    return;
                }
                TemperatureZone z = zoneManager.getZone(args[2]);
                if (z == null) {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                    return;
                }
                zoneManager.showZonePreview(player, z);
                msg(sender, "<green>エリア <yellow>" + z.name() + " <green>の境界を "
                        + config.getPreviewDurationSeconds() + "秒間 Display 表示します。");
            }
            case "hide" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
                    return;
                }
                zoneManager.clearPreview(player.getUniqueId());
                msg(sender, "<green>エリア境界の表示を消去しました。");
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /ltm zone tp <エリア名>");
                    return;
                }
                TemperatureZone z = zoneManager.getZone(args[2]);
                if (z == null) {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                    return;
                }
                World world = Bukkit.getWorld(z.worldName());
                if (world == null) {
                    msg(sender, "<red>ワールドが読み込まれていません: " + z.worldName());
                    return;
                }
                player.teleport(new Location(world, z.centerX(), z.centerY(), z.centerZ()));
                msg(sender, "<green>エリア <yellow>" + z.name() + " <green>の中心へテレポートしました。");
            }
            default -> msg(sender, "<red>使用法: /ltm zone <create|delete|list|info|show|hide|tp>");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        reloadAction.run();
        msg(sender, "<green>config.yml および zones.yml をリロードしました。");
    }

    private void sendHelp(CommandSender sender, String label) {
        msg(sender, "<yellow>=== LifeTempManager コマンド一覧 ===");
        msg(sender, "<gold>/" + label + " info [player] <gray>- 温度・即死ライン・防具耐性を表示");
        if (config.isAdmin(sender)) {
            msg(sender, "<gold>/" + label + " set <player> <温度> <gray>- 温度を設定");
            msg(sender, "<gold>/" + label + " add <player> <変化量> <gray>- 温度を加算/減算");
            msg(sender, "<gold>/" + label + " reset <player> <gray>- 温度をデフォルト値にリセット");
            msg(sender, "<gold>/" + label + " armor <set|clear|inspect> <gray>- 手持ち防具の耐性タグ操作");
            msg(sender, "<gold>/" + label + " zone <create|delete|list|info|show|hide|tp> <gray>- 空間温度エリア管理 (WorldEdit選択連携)");
            msg(sender, "<gold>/" + label + " reload <gray>- 設定をリロード");
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!config.isAdmin(sender)) {
            msg(sender, "<red>このコマンドを実行する権限がありません。");
            return false;
        }
        return true;
    }

    private void msg(CommandSender sender, String miniMessage) {
        sender.sendMessage(MINI.deserialize(PREFIX + miniMessage));
    }

    private static Double parseDouble(String raw) {
        try {
            double v = Double.parseDouble(raw);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        boolean admin = config.isAdmin(sender);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("info");
            if (admin) {
                subs.addAll(List.of("set", "add", "reset", "armor", "zone", "reload"));
            }
            return filterPrefix(subs, args[0]);
        }
        if (!admin) {
            return List.of();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "info", "set", "add", "reset" -> filterPrefix(onlinePlayerNames(), args[1]);
                case "armor" -> filterPrefix(List.of("set", "clear", "inspect"), args[1]);
                case "zone" -> filterPrefix(List.of("create", "delete", "list", "info", "show", "hide", "tp"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if ("armor".equals(sub) && "set".equalsIgnoreCase(args[1])) {
                return filterPrefix(List.of("heat", "cold"), args[2]);
            }
            if ("zone".equals(sub) && List.of("delete", "remove", "info", "show", "tp").contains(args[1].toLowerCase(Locale.ROOT))) {
                List<String> names = zoneManager.getZones().stream().map(TemperatureZone::name).toList();
                return filterPrefix(names, args[2]);
            }
            if ("set".equals(sub)) {
                return filterPrefix(List.of("20", "80", "-60", "100", "-80"), args[2]);
            }
            if ("add".equals(sub)) {
                return filterPrefix(List.of("20", "-20", "40", "-40"), args[2]);
            }
        }
        if (args.length == 4) {
            if ("armor".equals(sub) && "set".equalsIgnoreCase(args[1])) {
                return filterPrefix(List.of("50", "100"), args[3]);
            }
            if ("zone".equals(sub) && "create".equalsIgnoreCase(args[1])) {
                return filterPrefix(List.of("5", "-5", "10", "-10"), args[3]);
            }
        }
        if (args.length == 5) {
            if ("armor".equals(sub) && "set".equalsIgnoreCase(args[1])) {
                return filterPrefix(List.of("1", "2"), args[4]);
            }
            if ("zone".equals(sub) && "create".equalsIgnoreCase(args[1])) {
                return filterPrefix(List.of("10", "5", "20"), args[4]);
            }
        }
        return List.of();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> filterPrefix(List<String> candidates, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}

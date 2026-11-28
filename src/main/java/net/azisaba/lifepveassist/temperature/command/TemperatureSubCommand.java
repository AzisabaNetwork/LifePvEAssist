package net.azisaba.lifepveassist.temperature.command;

import net.azisaba.lifepveassist.temperature.armor.ArmorInspector;
import net.azisaba.lifepveassist.temperature.config.TemperatureConfig;
import net.azisaba.lifepveassist.temperature.core.TemperatureService;
import net.azisaba.lifepveassist.temperature.model.ArmorResistance;
import net.azisaba.lifepveassist.temperature.model.TemperatureEvaluation;
import net.azisaba.lifepveassist.temperature.ui.BossBarController;
import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;
import net.azisaba.lifepveassist.temperature.zone.WorldEditSelectionProvider;
import net.azisaba.lifepveassist.temperature.zone.ZoneManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /lifepveassist temp (/lpa temp) サブコマンド実装およびタブ補完。
 */
public class TemperatureSubCommand {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold>[LifePvEAssist] <reset>";

    private static final String PERM_USE = "lifepveassist.temp.use";
    private static final String PERM_ADMIN = "lifepveassist.temp.admin";
    private static final String PERM_BYPASS = "lifepveassist.temp.bypass";

    private final Plugin plugin;
    private final TemperatureService service;
    private final ArmorInspector armorInspector;
    private final ZoneManager zoneManager;
    private final Runnable reloadAction;

    public TemperatureSubCommand(
            Plugin plugin,
            TemperatureService service,
            ArmorInspector armorInspector,
            ZoneManager zoneManager,
            Runnable reloadAction
    ) {
        this.plugin = plugin;
        this.service = service;
        this.armorInspector = armorInspector;
        this.zoneManager = zoneManager;
        this.reloadAction = reloadAction;
    }

    /**
     * @param args "temp" の後ろに続く引数配列
     */
    public boolean execute(CommandSender sender, String rootLabel, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, rootLabel);
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
            case "bypass" -> handleBypass(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender, rootLabel);
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
                msg(sender, "<red>同一サーバー内にオンラインプレイヤーが見つかりません: " + args[1]);
                return;
            }
        } else if (sender instanceof Player p) {
            if (!p.hasPermission(PERM_USE)) {
                msg(sender, "<red>このコマンドを実行する権限がありません。");
                return;
            }
            target = p;
        } else {
            msg(sender, "<red>コンソールからは対象プレイヤーを指定してください: /lpa temp info <player>");
            return;
        }

        TemperatureConfig cfg = service.getConfig();
        ArmorResistance res = armorInspector.inspectPlayer(target);
        TemperatureEvaluation eval = service.evaluateCurrent(target);
        boolean activeWorld = cfg.isEnabledWorld(target.getWorld());

        msg(sender, "<yellow>=== " + target.getName() + " の温度情報 ===");
        msg(sender, "<gray>現在ワールド: <white>" + target.getWorld().getName()
                + (activeWorld ? " <green>(有効)" : " <dark_gray>(対象外)"));
        msg(sender, "<gray>現在温度: <white>" + BossBarController.formatTemp(eval.temperature()) + "℃"
                + " <gray>| 状態: <white>" + eval.severity().name()
                + (service.isBypassEnabled(target) ? " <yellow>[バイパスON]" : ""));
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
            msg(sender, "<red>使用法: /lpa temp set <player> <温度>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>同一サーバー内にオンラインプレイヤーが見つかりません: " + args[1]);
            return;
        }
        Double value = parseDouble(args[2]);
        if (value == null) {
            msg(sender, "<red>有効な数値を指定してください: " + args[2]);
            return;
        }
        if (!service.setTemperatureByAdmin(target, value)) {
            msg(sender, "<red>対象プレイヤーの温度データが読み込み中のため変更できませんでした。");
            return;
        }
        msg(sender, "<green>" + target.getName() + " の温度を <white>"
                + BossBarController.formatTemp(service.getTemperature(target)) + "℃ <green>に設定しました。");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "<red>使用法: /lpa temp add <player> <変化量>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>同一サーバー内にオンラインプレイヤーが見つかりません: " + args[1]);
            return;
        }
        Double delta = parseDouble(args[2]);
        if (delta == null) {
            msg(sender, "<red>有効な数値を指定してください: " + args[2]);
            return;
        }
        if (!service.addTemperatureByAdmin(target, delta)) {
            msg(sender, "<red>対象プレイヤーの温度データが読み込み中のため変更できませんでした。");
            return;
        }
        msg(sender, "<green>" + target.getName() + " の温度を <white>"
                + (delta >= 0 ? "+" : "") + BossBarController.formatTemp(delta)
                + "℃ <green>変更しました (現在: <white>"
                + BossBarController.formatTemp(service.getTemperature(target)) + "℃<green>)。");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "<red>使用法: /lpa temp reset <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "<red>同一サーバー内にオンラインプレイヤーが見つかりません: " + args[1]);
            return;
        }
        if (!service.resetTemperatureByAdmin(target)) {
            msg(sender, "<red>対象プレイヤーの温度データが読み込み中のためリセットできませんでした。");
            return;
        }
        msg(sender, "<green>" + target.getName() + " の温度をデフォルト ("
                + BossBarController.formatTemp(service.getConfig().defaultTemp()) + "℃) にリセットしました。");
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
            msg(sender, "<red>使用法: /lpa temp armor <set|clear|inspect>");
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
                    msg(sender, "<red>使用法: /lpa temp armor set <heat|cold> <延長温度(>0)>");
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
                if (bonus == null || bonus <= 0.0) {
                    msg(sender, "<red>延長温度は 0 より大きい数値で指定してください。");
                    return;
                }
                boolean ok = armorInspector.applyResistance(hand, isHeat, bonus);
                if (!ok) {
                    msg(sender, "<red>PDC の書き込みに失敗しました（値が既に同一か、編集不可のアイテムです）。");
                    return;
                }
                msg(sender, "<green>手持ちアイテムに PDC コンポーネント "
                        + (isHeat ? "<red>耐熱 (lifepveassist:temp_heat)" : "<aqua>耐冷 (lifepveassist:temp_cold)")
                        + " <white>+" + BossBarController.formatTemp(bonus) + "℃ <green>を付与しました。");
            }
            case "clear" -> {
                boolean ok = armorInspector.clearResistance(hand);
                if (!ok) {
                    msg(sender, "<red>温度耐性 PDC の削除に失敗しました（対象 PDC が付いていないか、編集不可のアイテムです）。");
                    return;
                }
                msg(sender, "<green>手持ちアイテムの温度耐性 PDC コンポーネントを削除しました。");
            }
            case "inspect" -> {
                ArmorResistance r = armorInspector.inspectItem(hand);
                msg(sender, "<yellow>=== アイテム耐性情報 (PDC コンポーネント) ===");
                msg(sender, "<gray>耐熱 (temp_heat): <red>+" + BossBarController.formatTemp(r.heatLimitBonus())
                        + "℃ (" + r.heatPieces() + "部位)");
                msg(sender, "<gray>耐冷 (temp_cold): <aqua>-" + BossBarController.formatTemp(r.coldLimitBonus())
                        + "℃ (" + r.coldPieces() + "部位)");
            }
            default -> msg(sender, "<red>使用法: /lpa temp armor <set|clear|inspect>");
        }
    }

    private void handleZone(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "<red>使用法: /lpa temp zone <create|delete|list|info|show|hide|tp>");
            return;
        }
        TemperatureConfig cfg = service.getConfig();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
                    return;
                }
                if (args.length < 5) {
                    msg(sender, "<red>使用法: /lpa temp zone create <エリア名> <変化量(0以外)> <間隔秒(1以上)>");
                    msg(sender, "<gray>※先に WorldEdit (//wand, //pos1, //pos2 等) で範囲を選択してください。");
                    return;
                }
                String name = args[2].trim();
                if (name.isEmpty() || name.length() > 64) {
                    msg(sender, "<red>エリア名は 1〜64 文字以内で指定してください。");
                    return;
                }
                if (zoneManager.getZone(name) != null) {
                    msg(sender, "<red>同名の温度エリア <yellow>" + name + " <red>は既に存在します（大文字・小文字区別なし）。");
                    return;
                }
                Double delta = parseDouble(args[3]);
                Integer interval = parseInt(args[4]);
                if (delta == null || Math.abs(delta) < 1e-9) {
                    msg(sender, "<red>変化量は 0 以外の数値で指定してください。");
                    return;
                }
                if (interval == null || interval < 1) {
                    msg(sender, "<red>間隔秒は 1 以上の整数で指定してください。");
                    return;
                }
                WorldEditSelectionProvider.SelectionResult result = WorldEditSelectionProvider.getSelection(player);
                if (!result.isSuccess()) {
                    msg(sender, "<red>" + result.errorMessage());
                    return;
                }
                if (!cfg.isEnabledWorld(result.bounds().worldName())) {
                    msg(sender, "<red>選択範囲のワールド (" + result.bounds().worldName()
                            + ") は temperature.enabled-worlds に含まれていないため作成できません。");
                    return;
                }
                TemperatureZone candidate = result.bounds().toZone(cfg.serverName(), name, delta, interval);
                TemperatureZone overlap = zoneManager.findOverlappingZone(candidate);
                if (overlap != null) {
                    msg(sender, "<red>既存エリア <yellow>" + overlap.name() + " <red>と範囲が重複しているため登録できません。");
                    return;
                }
                zoneManager.addZoneAsync(candidate).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!ok) {
                                msg(sender, "<red>DB へのエリア保存に失敗しました。コンソールログを確認してください。");
                                return;
                            }
                            msg(sender, "<green>温度エリア <yellow>" + candidate.name() + " <green>を DB へ登録しました！ "
                                    + "<gray>(" + candidate.serverName() + "/" + candidate.worldName() + " / "
                                    + interval + "秒ごとに " + (delta >= 0 ? "+" : "")
                                    + BossBarController.formatTemp(delta) + "℃ / " + candidate.volume() + "ブロック)");
                            if (player.isOnline()) {
                                zoneManager.showZonePreview(player, candidate, cfg.previewDurationSeconds());
                            }
                        })
                );
            }
            case "delete", "remove" -> {
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /lpa temp zone delete <エリア名>");
                    return;
                }
                String name = args[2];
                zoneManager.removeZoneAsync(cfg.serverName(), name).thenAccept(deleted ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (deleted) {
                                msg(sender, "<green>温度エリア <yellow>" + name + " <green>を DB から削除しました。");
                            } else {
                                msg(sender, "<red>指定されたエリアは存在しません: " + name);
                            }
                        })
                );
            }
            case "list" -> {
                if (zoneManager.getZones().isEmpty()) {
                    msg(sender, "<gray>登録されている温度エリアはありません (server=" + cfg.serverName() + ")。");
                    return;
                }
                msg(sender, "<yellow>=== 登録済み温度エリア一覧 (" + zoneManager.getZones().size()
                        + "件 / server=" + cfg.serverName() + ") ===");
                for (TemperatureZone z : zoneManager.getZones()) {
                    String sign = z.delta() >= 0 ? "<red>+" : "<aqua>";
                    msg(sender, "<white>- <yellow>" + z.name() + " <gray>[" + z.worldName() + "] "
                            + sign + BossBarController.formatTemp(z.delta()) + "℃ <gray>/ " + z.intervalSeconds() + "秒");
                }
            }
            case "info" -> {
                if (args.length < 3) {
                    msg(sender, "<red>使用法: /lpa temp zone info <エリア名>");
                    return;
                }
                TemperatureZone z = zoneManager.getZone(args[2]);
                if (z == null) {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                    return;
                }
                msg(sender, "<yellow>=== エリア情報: " + z.name() + " ===");
                msg(sender, "<gray>サーバー / ワールド: <white>" + z.serverName() + " / " + z.worldName());
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
                    msg(sender, "<red>使用法: /lpa temp zone show <エリア名>");
                    return;
                }
                TemperatureZone z = zoneManager.getZone(args[2]);
                if (z == null) {
                    msg(sender, "<red>指定されたエリアは存在しません: " + args[2]);
                    return;
                }
                zoneManager.showZonePreview(player, z, cfg.previewDurationSeconds());
                msg(sender, "<green>エリア <yellow>" + z.name() + " <green>の境界を "
                        + cfg.previewDurationSeconds() + "秒間 Display 表示します。");
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
                    msg(sender, "<red>使用法: /lpa temp zone tp <エリア名>");
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
            default -> msg(sender, "<red>使用法: /lpa temp zone <create|delete|list|info|show|hide|tp>");
        }
    }

    private void handleBypass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "<red>このコマンドはプレイヤーのみ実行できます。");
            return;
        }
        if (!player.hasPermission(PERM_BYPASS) && !player.hasPermission(PERM_ADMIN)) {
            msg(sender, "<red>このコマンドを実行する権限がありません。");
            return;
        }
        boolean enabled = service.toggleBypass(player);
        if (enabled) {
            msg(sender, "<yellow>温度バイパスモードを <green>ON <yellow>にしました（温度変化・デメリット・即死を免除 / ログアウトで自動OFF）。");
        } else {
            msg(sender, "<yellow>温度バイパスモードを <red>OFF <yellow>にしました。");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        reloadAction.run();
        msg(sender, "<green>config.yml (temperature:) および DB の Zone キャッシュをリロードしました（※database 接続設定は再起動で反映）。");
    }

    private void sendHelp(CommandSender sender, String rootLabel) {
        String base = "/" + rootLabel + " temp";
        msg(sender, "<yellow>=== 温度管理コマンド一覧 ===");
        msg(sender, "<gold>" + base + " info [player] <gray>- 温度・即死ライン・防具耐性を表示");
        if (sender.hasPermission(PERM_BYPASS) || sender.hasPermission(PERM_ADMIN)) {
            msg(sender, "<gold>" + base + " bypass <gray>- 自身の温度処理免除モードを ON/OFF 切替");
        }
        if (sender.hasPermission(PERM_ADMIN)) {
            msg(sender, "<gold>" + base + " set <player> <温度> <gray>- 温度を設定");
            msg(sender, "<gold>" + base + " add <player> <変化量> <gray>- 温度を加算/減算");
            msg(sender, "<gold>" + base + " reset <player> <gray>- 温度をデフォルト値にリセット");
            msg(sender, "<gold>" + base + " armor <set|clear|inspect> <gray>- 手持ち防具の PDC 耐性タグ操作");
            msg(sender, "<gold>" + base + " zone <create|delete|list|info|show|hide|tp> <gray>- Zone 管理 (WorldEdit連携)");
            msg(sender, "<gold>" + base + " reload <gray>- 設定および DB の Zone キャッシュを再読込");
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission(PERM_ADMIN);
        boolean canBypass = admin || sender.hasPermission(PERM_BYPASS);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(PERM_USE)) {
                subs.add("info");
            }
            if (canBypass) {
                subs.add("bypass");
            }
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
                return filterPrefix(zoneManager.getZones().stream().map(TemperatureZone::name).toList(), args[2]);
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
        if (args.length == 5 && "zone".equals(sub) && "create".equalsIgnoreCase(args[1])) {
            return filterPrefix(List.of("10", "5", "20"), args[4]);
        }
        return List.of();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
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

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filterPrefix(List<String> candidates, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}

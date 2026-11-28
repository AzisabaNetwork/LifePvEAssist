package net.azisaba.lifepveassist.temperature.zone;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * WorldEdit / FastAsyncWorldEdit の API 経由でプレイヤーの現在選択範囲を取得するプロバイダ。
 */
public final class WorldEditSelectionProvider {

    public record SelectionBounds(
            String worldName,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        public TemperatureZone toZone(String serverName, String name, double delta, int intervalSeconds) {
            return new TemperatureZone(
                    serverName, name, worldName,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    delta, Math.max(1, intervalSeconds)
            );
        }
    }

    public record SelectionResult(SelectionBounds bounds, String errorMessage) {
        public boolean isSuccess() {
            return bounds != null;
        }
    }

    private WorldEditSelectionProvider() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    public static SelectionResult getSelection(Player player) {
        if (!isAvailable()) {
            return new SelectionResult(null, "WorldEdit または FastAsyncWorldEdit が導入されていません。");
        }
        try {
            return readWorldEditSelection(player);
        } catch (NoClassDefFoundError e) {
            return new SelectionResult(null, "WorldEdit API の読み込みに失敗しました。");
        }
    }

    private static SelectionResult readWorldEditSelection(Player player) {
        BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
        if (session == null) {
            return new SelectionResult(null, "WorldEdit の選択セッションが見つかりません。先に //wand や //pos1・//pos2 で範囲を選択してください。");
        }
        try {
            Region region = session.getSelection(wePlayer.getWorld());
            if (region == null) {
                return new SelectionResult(null, "WorldEdit で範囲が選択されていません。");
            }
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            String worldName = (region.getWorld() != null)
                    ? region.getWorld().getName()
                    : player.getWorld().getName();
            return new SelectionResult(
                    new SelectionBounds(worldName, min.x(), min.y(), min.z(), max.x(), max.y(), max.z()),
                    null
            );
        } catch (IncompleteRegionException e) {
            return new SelectionResult(null, "WorldEdit の範囲選択が未完了です。pos1 と pos2 の両方を指定してください。");
        } catch (Exception e) {
            return new SelectionResult(null, "WorldEdit の選択範囲取得に失敗しました: " + e.getMessage());
        }
    }
}

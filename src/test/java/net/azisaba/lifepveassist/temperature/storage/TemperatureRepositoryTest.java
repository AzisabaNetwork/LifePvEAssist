package net.azisaba.lifepveassist.temperature.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 の MariaDB 互換モードを用いて TemperatureRepository の実 SQL・所有者ベース CAS ロック・
 * タイムアウト引継ぎ・古い所有者の上書き防止・Zone CRUD を検証するテスト。
 */
class TemperatureRepositoryTest {

    private HikariDataSource dataSource;
    private TemperatureRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:temp_test_" + UUID.randomUUID() + ";MODE=MariaDB;DATABASE_TO_LOWER=TRUE");
        cfg.setUsername("sa");
        cfg.setPassword("");
        this.dataSource = new HikariDataSource(cfg);
        this.repo = new TemperatureRepository(dataSource);
        this.repo.initTables();
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void testLoadWaitForLockReleaseOnServerTransfer() throws Exception {
        UUID uuid = UUID.randomUUID();
        String serverA = "serverA-boot1";
        String serverB = "serverB-boot1";

        // サーバーAがログインしてロック取得
        TemperatureRepository.LoadedPlayerRow first = repo.loadAndAcquireLock(uuid, serverA, 20.0, 1000L, 180_000L);
        assertFalse(first.existed());
        assertTrue(first.lockAcquired());
        assertEquals(20.0, first.temperature());

        // サーバーAが 250ms 後に 85.0℃ を保存してロック解放する状況をシミュレート
        CompletableFuture<Void> serverAQuit = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(250L);
                assertTrue(repo.savePlayerAndReleaseLock(uuid, serverA, 85.0));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // サーバーBが Join 後に最大 2秒待機して最新温度 85.0℃ を確実に取得できること
        TemperatureRepository.LoadedPlayerRow second = repo.loadAndAcquireLock(uuid, serverB, 20.0, 2000L, 180_000L);
        serverAQuit.join();

        assertTrue(second.existed());
        assertTrue(second.lockAcquired());
        assertEquals(85.0, second.temperature(), 0.001);
    }

    @Test
    void testLiveOwnerLockCannotBeStolenAndStaleSaveIsIgnored() throws Exception {
        UUID uuid = UUID.randomUUID();
        String serverA = "serverA-boot1";
        String serverB = "serverB-boot1";

        // サーバーAがロック取得＋定期保存で 42.0℃ を書き込み
        assertTrue(repo.loadAndAcquireLock(uuid, serverA, 20.0, 500L, 180_000L).lockAcquired());
        repo.savePlayersBatch(serverA, Map.of(uuid, 42.0), true);

        // サーバーAのロックが生きている間は、サーバーBはロックを奪えない (lockAcquired == false)
        TemperatureRepository.LoadedPlayerRow stolenAttempt = repo.loadAndAcquireLock(uuid, serverB, 20.0, 150L, 180_000L);
        assertTrue(stolenAttempt.existed());
        assertFalse(stolenAttempt.lockAcquired());
        assertEquals(42.0, stolenAttempt.temperature(), 0.001);

        // サーバーAのハートビート更新も成功すること
        repo.renewLocksBatch(serverA, List.of(uuid));

        // クラッシュ想定 (lockTimeout=0ms) でサーバーBが正当に引き継ぐ
        TemperatureRepository.LoadedPlayerRow takeover = repo.loadAndAcquireLock(uuid, serverB, 20.0, 500L, 0L);
        assertTrue(takeover.lockAcquired());
        repo.savePlayersBatch(serverB, Map.of(uuid, 90.0), true);

        // 古い所有者であるサーバーAの遅延オートセーブ (33.0℃) は無視され、サーバーBの 90.0℃ が守られること
        repo.savePlayersBatch(serverA, Map.of(uuid, 33.0), true);
        assertFalse(repo.savePlayerAndReleaseLock(uuid, serverA, 33.0));

        // サーバーBが正常解放すると 90.0℃ が残っていること
        assertTrue(repo.savePlayerAndReleaseLock(uuid, serverB, 90.0));
        TemperatureRepository.LoadedPlayerRow finalRow = repo.loadAndAcquireLock(uuid, "serverC-boot1", 20.0, 500L, 180_000L);
        assertTrue(finalRow.lockAcquired());
        assertEquals(90.0, finalRow.temperature(), 0.001);
    }

    @Test
    void testLockTimeoutFallbackAfterCrash() throws Exception {
        UUID uuid = UUID.randomUUID();
        String crashedServer = "crashed-boot1";
        String nextServer = "next-boot1";

        assertTrue(repo.loadAndAcquireLock(uuid, crashedServer, 20.0, 500L, 180_000L).lockAcquired());
        repo.savePlayersBatch(crashedServer, Map.of(uuid, 65.0), true);

        // lockTimeout=0ms ならタイムアウトとみなして引き継げる
        TemperatureRepository.LoadedPlayerRow loaded = repo.loadAndAcquireLock(uuid, nextServer, 20.0, 500L, 0L);
        assertTrue(loaded.existed());
        assertTrue(loaded.lockAcquired());
        assertEquals(65.0, loaded.temperature(), 0.001);
    }

    @Test
    void testZoneCrudAndServerSeparation() throws Exception {
        TemperatureZone z1 = new TemperatureZone("ura1", "lava_room", "world", 0, 0, 0, 10, 10, 10, 5.0, 10);
        TemperatureZone z2 = new TemperatureZone("ura2", "lava_room", "world", 50, 0, 50, 60, 10, 60, -8.0, 5);

        repo.upsertZone(z1);
        repo.upsertZone(z2);

        List<TemperatureZone> ura1Zones = repo.loadZones("ura1");
        assertEquals(1, ura1Zones.size());
        assertEquals(5.0, ura1Zones.get(0).delta());

        List<TemperatureZone> ura2Zones = repo.loadZones("ura2");
        assertEquals(1, ura2Zones.size());
        assertEquals(-8.0, ura2Zones.get(0).delta());

        assertTrue(repo.deleteZone("ura1", "LAVA_ROOM"));
        assertTrue(repo.loadZones("ura1").isEmpty());
        assertEquals(1, repo.loadZones("ura2").size());
    }
}

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
 * H2 の MariaDB 互換モードを用いて TemperatureRepository の実 SQL・ロック待機・
 * タイムアウト引継ぎ・Zone CRUD を検証するテスト。
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

        // サーバーAがログインしてロック取得
        TemperatureRepository.LoadedPlayerRow first = repo.loadAndAcquireLock(uuid, 20.0, 1000L, 10_000L);
        assertFalse(first.existed());
        assertEquals(20.0, first.temperature());

        // サーバーAが 250ms 後に 85.0℃ を保存してロック解放する状況をシミュレート
        CompletableFuture<Void> serverAQuit = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(250L);
                repo.savePlayerAndReleaseLock(uuid, 85.0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // サーバーBが Join 後に最大 2秒待機して最新温度 85.0℃ を確実に取得できること
        TemperatureRepository.LoadedPlayerRow second = repo.loadAndAcquireLock(uuid, 20.0, 2000L, 10_000L);
        serverAQuit.join();

        assertTrue(second.existed());
        assertEquals(85.0, second.temperature(), 0.001);
    }

    @Test
    void testLockTimeoutFallbackAfterCrash() throws Exception {
        UUID uuid = UUID.randomUUID();
        repo.savePlayersBatch(Map.of(uuid, 65.0), true); // locked=true のままクラッシュ想定

        // lockTimeout=0ms なら即座にタイムアウトとみなして引き継げる
        TemperatureRepository.LoadedPlayerRow loaded = repo.loadAndAcquireLock(uuid, 20.0, 500L, 0L);
        assertTrue(loaded.existed());
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

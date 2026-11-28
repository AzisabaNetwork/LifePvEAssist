package net.azisaba.lifepveassist.temperature.model;

import net.azisaba.lifepveassist.temperature.zone.TemperatureZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureCalculatorTest {

    private TemperatureEvaluation eval(double temp, ArmorResistance res) {
        return TemperatureCalculator.evaluate(
                temp, 20.0, -80.0, 100.0, res,
                40.0, 20.0, 20.0, 2, 4
        );
    }

    @Test
    void testClampingAndInvalidNumbers() {
        assertEquals(1000.0, TemperatureCalculator.clampTemperature(9999.0, 20.0));
        assertEquals(-1000.0, TemperatureCalculator.clampTemperature(-9999.0, 20.0));
        assertEquals(20.0, TemperatureCalculator.clampTemperature(Double.NaN, 20.0));
        assertEquals(20.0, TemperatureCalculator.clampTemperature(Double.POSITIVE_INFINITY, 20.0));
        assertEquals(45.5, TemperatureCalculator.clampTemperature(45.5, 20.0));
    }

    @Test
    void testOfflineRecovery() {
        // 10秒以内は回復しない
        assertEquals(90.0, TemperatureCalculator.applyOfflineRecovery(90.0, 20.0, 10, 10, 20.0));
        // 12秒経過 -> 2秒分 (40℃) 回復 -> 50℃
        assertEquals(50.0, TemperatureCalculator.applyOfflineRecovery(90.0, 20.0, 12, 10, 20.0));
        // 長時間経過 -> 20℃で停止
        assertEquals(20.0, TemperatureCalculator.applyOfflineRecovery(90.0, 20.0, 3600, 10, 20.0));
        // 低温側 (-60℃) で12秒経過 -> +40℃ -> -20℃
        assertEquals(-20.0, TemperatureCalculator.applyOfflineRecovery(-60.0, 20.0, 12, 10, 20.0));
        assertEquals(20.0, TemperatureCalculator.applyOfflineRecovery(-60.0, 20.0, 100, 10, 20.0));
    }

    @Test
    void testArmorThresholdsAndFullSetVisualRetention() {
        ArmorResistance fullHeat = new ArmorResistance(4, 200.0, 0, 0.0);
        TemperatureEvaluation hot2 = eval(280.0, fullHeat);
        assertEquals(300.0, hot2.maxLimit());
        assertFalse(hot2.applySlownessBlindness());
        assertFalse(hot2.applyContinuousDamage());
        assertTrue(hot2.applyHeatVisual());
        assertTrue(hot2.inWarningZone());
        assertFalse(hot2.triggerLethal());

        assertTrue(eval(300.0, fullHeat).triggerLethal());

        // 複合防具2部位 (耐熱2 + 耐冷2)
        ArmorResistance hybrid = new ArmorResistance(2, 100.0, 2, 100.0);
        TemperatureEvaluation hotH = eval(180.0, hybrid);
        assertFalse(hotH.applySlownessBlindness());
        assertTrue(hotH.applyContinuousDamage());
        TemperatureEvaluation coldH = eval(-160.0, hybrid);
        assertFalse(coldH.applySlownessBlindness());
        assertTrue(coldH.applyContinuousDamage());
        assertTrue(coldH.applyColdVisual());
    }

    @Test
    void testZoneIntersectionAndServerIsolation() {
        TemperatureZone z1 = new TemperatureZone("srv1", "hot_a", "world", 0, 0, 0, 10, 10, 10, 5.0, 10);
        TemperatureZone zOverlap = new TemperatureZone("srv1", "hot_b", "world", 10, 5, 5, 20, 15, 15, 10.0, 10);
        TemperatureZone zDisjoint = new TemperatureZone("srv1", "hot_c", "world", 11, 0, 0, 20, 10, 10, 5.0, 10);
        TemperatureZone zOtherServer = new TemperatureZone("srv2", "hot_a", "world", 0, 0, 0, 10, 10, 10, 5.0, 10);

        assertTrue(z1.intersects(zOverlap));
        assertFalse(z1.intersects(zDisjoint));
        assertFalse(z1.intersects(zOtherServer));
    }
}

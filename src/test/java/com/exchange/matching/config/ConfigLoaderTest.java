package com.exchange.matching.config;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    @DisplayName("Should correctly load real values from config.properties")
    void testLoadRealConfigProperties() {
        ConfigLoader loader = new ConfigLoader();

        assertEquals(65536, loader.getDisruptorRingBufferSize());
        assertTrue(loader.getDisruptorWaitStrategy() instanceof YieldingWaitStrategy);
        assertTrue(loader.isDisruptorUseVirtualThreads());

        List<String> symbols = loader.getSupportedSymbols();
        assertNotNull(symbols);
        assertEquals(3, symbols.size());
        assertEquals("BTCUSDT", symbols.get(0));
        assertEquals("ETHUSDT", symbols.get(1));
        assertEquals("SOLUSDT", symbols.get(2));
        assertEquals("BTCUSDT", loader.getPrimarySymbol());

        assertEquals(100000, loader.getOrderPoolMaxCapacity());
        assertEquals(100000, loader.getTradePoolMaxCapacity());
        assertTrue(loader.isPoolPrefillEnabled());
    }

    @Test
    @DisplayName("Should correctly fall back to default values for missing keys")
    void testFallbackToDefaultsForMissingKeys() {
        ConfigLoader loader = new ConfigLoader();

        assertEquals("default-value", loader.getString("non.existent.key", "default-value"));
        assertEquals(999, loader.getInt("non.existent.int", 999));
        assertFalse(loader.getBoolean("non.existent.bool", false));
        assertTrue(loader.getBoolean("non.existent.bool.true", true));

        List<String> defaultList = List.of("DEFAULT1", "DEFAULT2");
        assertEquals(defaultList, loader.getStringList("non.existent.list", defaultList));
    }

    @Test
    @DisplayName("Should handle missing resource file gracefully and return defaults")
    void testMissingConfigFileFallback() {
        ConfigLoader loader = new ConfigLoader("non-existent-config-file.properties");

        assertEquals(65536, loader.getDisruptorRingBufferSize());
        assertEquals("BTCUSDT", loader.getPrimarySymbol());
        assertEquals(100000, loader.getOrderPoolMaxCapacity());
        assertEquals("fallback", loader.getString("any.key", "fallback"));
    }

    @Test
    @DisplayName("Should handle invalid numeric property values gracefully")
    void testInvalidNumericPropertyFallback() {
        Properties props = new Properties();
        props.setProperty("disruptor.ringbuffer.size", "not-a-number");
        ConfigLoader loader = new ConfigLoader(props);

        assertEquals(1024, loader.getInt("disruptor.ringbuffer.size", 1024));
    }

    @Test
    @DisplayName("Should correctly parse different WaitStrategy string names")
    void testParseWaitStrategyNames() {
        Properties props = new Properties();

        props.setProperty("strategy", "BUSY_SPIN");
        assertTrue(new ConfigLoader(props).getWaitStrategy("strategy", "YIELDING") instanceof BusySpinWaitStrategy);

        props.setProperty("strategy", "YIELDING");
        assertTrue(new ConfigLoader(props).getWaitStrategy("strategy", "BUSY_SPIN") instanceof YieldingWaitStrategy);

        props.setProperty("strategy", "BLOCKING");
        assertTrue(new ConfigLoader(props).getWaitStrategy("strategy", "YIELDING") instanceof BlockingWaitStrategy);

        props.setProperty("strategy", "SLEEPING");
        assertTrue(new ConfigLoader(props).getWaitStrategy("strategy", "YIELDING") instanceof SleepingWaitStrategy);

        props.setProperty("strategy", "UNKNOWN_STRATEGY");
        assertTrue(new ConfigLoader(props).getWaitStrategy("strategy", "YIELDING") instanceof YieldingWaitStrategy);
    }
}

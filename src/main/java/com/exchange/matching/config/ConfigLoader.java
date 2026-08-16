package com.exchange.matching.config;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility class for loading application properties from the classpath with typed getters and fallback defaults.
 */
public class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    public static final String DEFAULT_CONFIG_FILE = "config.properties";

    private final Properties properties;

    /**
     * Loads configuration from default "config.properties" file on the classpath.
     */
    public ConfigLoader() {
        this(DEFAULT_CONFIG_FILE);
    }

    /**
     * Loads configuration from specified resource file on the classpath.
     *
     * @param resourceName name of properties file on classpath
     */
    public ConfigLoader(String resourceName) {
        this.properties = new Properties();
        loadFromClasspath(resourceName);
    }

    /**
     * Constructs ConfigLoader with explicitly provided Properties instance.
     *
     * @param properties pre-populated properties instance
     */
    public ConfigLoader(Properties properties) {
        this.properties = properties != null ? properties : new Properties();
    }

    /**
     * Factory method to load configuration from default "config.properties" file.
     *
     * @return ConfigLoader instance
     */
    public static ConfigLoader load() {
        return new ConfigLoader();
    }

    /**
     * Factory method to load configuration from specified resource file.
     *
     * @param resourceName name of properties file on classpath
     * @return ConfigLoader instance
     */
    public static ConfigLoader load(String resourceName) {
        return new ConfigLoader(resourceName);
    }

    private void loadFromClasspath(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ConfigLoader.class.getClassLoader();
        }

        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                properties.load(inputStream);
                LOGGER.info(() -> "Successfully loaded configuration properties from " + resourceName);
            } else {
                LOGGER.warning(() -> "Configuration file '" + resourceName + "' not found on classpath. Falling back to default settings.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read configuration file '" + resourceName + "'. Using fallback defaults.", e);
        }
    }

    /**
     * Returns string value for property key or default value if missing/empty.
     *
     * @param key          property key
     * @param defaultValue default value
     * @return string property value
     */
    public String getString(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Returns integer value for property key or default value if missing or invalid.
     *
     * @param key          property key
     * @param defaultValue default integer value
     * @return integer property value
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning(() -> String.format("Invalid integer property for key '%s': '%s'. Falling back to default: %d", key, value, defaultValue));
            return defaultValue;
        }
    }

    /**
     * Returns boolean value for property key or default value if missing.
     *
     * @param key          property key
     * @param defaultValue default boolean value
     * @return boolean property value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Returns list of strings for comma-separated property key or default value if missing.
     *
     * @param key          property key
     * @param defaultValue default list of strings
     * @return list of trimmed string items
     */
    public List<String> getStringList(String key, List<String> defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        List<String> list = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return list.isEmpty() ? defaultValue : Collections.unmodifiableList(list);
    }

    /**
     * Gets RingBuffer size configured via 'disruptor.ringbuffer.size' (default: 65536).
     *
     * @return ring buffer capacity
     */
    public int getDisruptorRingBufferSize() {
        return getInt("disruptor.ringbuffer.size", 65536);
    }

    /**
     * Gets LMAX WaitStrategy configured via 'disruptor.wait.strategy' (default: YIELDING).
     *
     * @return LMAX Disruptor WaitStrategy
     */
    public WaitStrategy getDisruptorWaitStrategy() {
        return getWaitStrategy("disruptor.wait.strategy", "YIELDING");
    }

    /**
     * Parses strategy name into appropriate LMAX Disruptor WaitStrategy instance.
     *
     * @param key                 property key
     * @param defaultStrategyName default strategy name if key is missing/unrecognized
     * @return LMAX WaitStrategy instance
     */
    public WaitStrategy getWaitStrategy(String key, String defaultStrategyName) {
        String name = getString(key, defaultStrategyName).toUpperCase();
        switch (name) {
            case "BUSY_SPIN":
            case "BUSYSPIN":
                return new BusySpinWaitStrategy();
            case "YIELDING":
                return new YieldingWaitStrategy();
            case "BLOCKING":
                return new BlockingWaitStrategy();
            case "SLEEPING":
                return new SleepingWaitStrategy();
            default:
                LOGGER.warning(() -> String.format("Unrecognized wait strategy '%s'. Falling back to YIELDING strategy.", name));
                return new YieldingWaitStrategy();
        }
    }

    /**
     * Gets whether virtual threads are configured for Disruptor worker threads (default: true).
     *
     * @return boolean flag
     */
    public boolean isDisruptorUseVirtualThreads() {
        return getBoolean("disruptor.use.virtual.threads", true);
    }

    /**
     * Gets supported symbols configured via 'engine.supported.symbols'.
     *
     * @return list of supported symbol strings
     */
    public List<String> getSupportedSymbols() {
        return getStringList("engine.supported.symbols", List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"));
    }

    /**
     * Gets primary symbol (first item from supported symbols list or default 'BTCUSDT').
     *
     * @return primary trading symbol
     */
    public String getPrimarySymbol() {
        List<String> symbols = getSupportedSymbols();
        return (symbols != null && !symbols.isEmpty()) ? symbols.get(0) : "BTCUSDT";
    }

    /**
     * Gets max capacity bound for OrderPool via 'pool.orders.max.capacity' (default: 100000).
     *
     * @return max order pool capacity
     */
    public int getOrderPoolMaxCapacity() {
        return getInt("pool.orders.max.capacity", 100000);
    }

    /**
     * Gets max capacity bound for TradePool via 'pool.trades.max.capacity' (default: 100000).
     *
     * @return max trade pool capacity
     */
    public int getTradePoolMaxCapacity() {
        return getInt("pool.trades.max.capacity", 100000);
    }

    /**
     * Gets whether pool prefilling is enabled via 'pool.prefill.enabled' (default: true).
     *
     * @return boolean flag
     */
    public boolean isPoolPrefillEnabled() {
        return getBoolean("pool.prefill.enabled", true);
    }

    /**
     * Gets underlying Properties object.
     *
     * @return properties instance
     */
    public Properties getProperties() {
        return properties;
    }
}

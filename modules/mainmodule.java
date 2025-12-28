package modules;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class mainmodule {

    private final Map<String, Object> config = new HashMap<>();

    public void sayHello() {
        System.out.println("Hello from Module!");
    }

    public void configLoad() {
        try (InputStream in = mainmodule.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    config.put(key, parseValue(value));
                }
            } else {
                System.err.println("Config file not found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object parseValue(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.contains(".")) return Double.parseDouble(value);
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    public Object get(String key) {
        return config.get(key);
    }

    public String getString(String key) {
        Object val = config.get(key);
        return val != null ? val.toString() : null;
    }

    public int getInt(String key, int defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public double getDouble(String key, double defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = config.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        try { return Boolean.parseBoolean(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public Map<String, Object> getAllConfig() {
        return new HashMap<>(config);
    }
}

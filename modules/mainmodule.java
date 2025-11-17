package modules;

import java.io.InputStream;
import java.util.Properties;
import java.lang.reflect.Field;

public class mainmodule {

    public String teststring;
    public int testnumber;
    public boolean testboolean;
    public double testdouble;

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
                    Field field;
                    try {
                        field = mainmodule.class.getDeclaredField(key);
                    } catch (NoSuchFieldException e) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object parsed = parseValue(value, field.getType());
                    field.set(this, parsed);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object parseValue(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        return null;
    }
}

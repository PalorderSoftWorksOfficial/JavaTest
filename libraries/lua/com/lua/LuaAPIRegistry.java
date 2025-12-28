package libraries.lua.com.lua;

import java.util.HashMap;

public class LuaAPIRegistry {

    private static final HashMap<String, Object> apis = new HashMap<>();

    /** Register all builtin Lua APIs */
    public static void registerBuiltin() {
        register("tonumber", (LuaFunction) (args) -> lua.tonumber((String) args[0]));
        register("tostring", (LuaFunction) (args) -> lua.tostring(args[0]));
        register("sleep", (LuaFunction) (args) -> {
            lua.sleep((long) args[0]);
        });
        register("print", (LuaFunction) lua::print);

        // Sub-APIs
        register("math", new lua.math());
        register("string", new lua.string());
        register("table", new lua.table());

    }
    public static class MinecraftChatAPI {}
    public static class MinecraftWorldAPI {}
    public static class MinecraftPlayerAPI {}
    /** Register a named API */
    public static void register(String name, Object api) {
        apis.put(name.toLowerCase(), api);
    }

    /** Get an API by name (for your scripts or calls) */
    public static Object get(String name) {
        return apis.get(name.toLowerCase());
    }


    /* -------------------------------------------------------------
       OPTIONAL MINECRAFT APIs
       Loaded ONLY if the MC classes exist in classpath (Forge ✔ Fabric ✔)
       ------------------------------------------------------------- */
    public static void registerMinecraftAPIs() {
        // Add Forge API wrapper ONLY if Forge classes are present
        if (classExists("net.minecraftforge.fml.common.Mod")) {
            register("chat", new MinecraftChatAPI());
            register("world", new MinecraftWorldAPI());
            register("player", new MinecraftPlayerAPI());
        }
    }

    private static boolean classExists(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /* -------------------------------------------------------------
       Functional interfaces (Lua-like)
       ------------------------------------------------------------- */

    public interface LuaFunction {
        void call(Object... args);
    }

    public interface LuaFunction1 {
        Object call(Object a);
    }

    public interface LuaFunction2 {
        Object call(long a);
    }
}

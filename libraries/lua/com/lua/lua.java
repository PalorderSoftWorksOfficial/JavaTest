package libraries.lua.com.lua;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.script.*;

/**
 * Extended Lua-style helper library with io.run, io.eval and Minecraft/Forge-aware chat hooks.
 *
 * - Uses reflection to detect Minecraft/Forge classes at runtime.
 * - When Minecraft APIs are present, chat(...) will attempt to send client/server messages.
 * - When not present, chat(...) falls back to stdout (simulation).
 */
public class lua {

    /* ---------------- Basic functions (from your previous version) ---------------- */

    public static void print(Object... args) {
        if (args == null || args.length == 0) {
            System.out.println("nil");
            return;
        }
        for (Object o : args) {
            System.out.println(tostring(o));
        }
    }

    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public static int tonumber(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String tostring(Object o) {
        return String.valueOf(o);
    }

    /* ---------------- Tables / math / string / warn / error as before ---------------- */

    public static class table {
        private java.util.HashMap<Object, Object> map = new java.util.HashMap<>();

        public void set(Object key, Object value) { map.put(key, value); }
        public Object get(Object key) { return map.get(key); }
        public boolean contains(Object key) { return map.containsKey(key); }
        public void remove(Object key) { map.remove(key); }
        public java.util.Set<Object> keys() { return map.keySet(); }
        public java.util.Collection<Object> values() { return map.values(); }
        public int length() { return map.size(); }
        @Override public String toString() { return map.toString(); }
    }
    public static table table() { return new table(); }

    public static class math {
        public static double abs(double x) { return Math.abs(x); }
        public static double sqrt(double x) { return Math.sqrt(x); }
        public static double sin(double x) { return Math.sin(x); }
        public static double cos(double x) { return Math.cos(x); }
        public static double tan(double x) { return Math.tan(x); }
        public static double floor(double x) { return Math.floor(x); }
        public static double ceil(double x) { return Math.ceil(x); }
        public static int random(int min, int max) {
            return (int) (Math.random() * (max - min + 1)) + min;
        }
    }

    public static class string {
        public static int len(String s) { return s.length(); }
        public static String upper(String s) { return s.toUpperCase(); }
        public static String lower(String s) { return s.toLowerCase(); }
        public static boolean contains(String s, String find) { return s.contains(find); }
        public static String sub(String s, int start, int end) { return s.substring(start - 1, end); }
        public static String rep(String s, int n) { return s.repeat(n); }
    }

    public static void warn(Object msg) { System.out.println("[WARN] " + msg); }
    public static void error(Object msg) { System.out.println("[ERROR] " + msg); }

    /* ---------------- IO: run and eval ---------------- */

    /**
     * Result returned by io.run
     */
    public static class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode; this.stdout = stdout; this.stderr = stderr;
        }
        @Override public String toString() {
            return "exit=" + exitCode + " stdout=" + stdout + " stderr=" + stderr;
        }
    }

    /**
     * Run an OS command synchronously. Returns stdout/stderr and exit code.
     * Example: io.run("echo hello")
     */
    public static ProcessResult io_run(String command) {
        try {
            // On Windows keep using "cmd /c", on Unix use "sh -c"
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);

            pb.redirectErrorStream(false);
            Process p = pb.start();

            // read stdout and stderr concurrently
            Future<String> outF = readStreamAsync(p.getInputStream());
            Future<String> errF = readStreamAsync(p.getErrorStream());

            int exit = p.waitFor();
            String out = outF.get(5, TimeUnit.SECONDS);
            String err = errF.get(5, TimeUnit.SECONDS);
            return new ProcessResult(exit, out, err);
        } catch (Exception e) {
            return new ProcessResult(-1, "", e.getMessage());
        }
    }

    private static Future<String> readStreamAsync(InputStream is) {
        ExecutorService es = Executors.newSingleThreadExecutor();
        return es.submit(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) {
                    sb.append(l).append("\n");
                }
                return sb.toString();
            } finally {
                es.shutdown();
            }
        });
    }

    /**
     * Evaluate a script using the Java ScriptEngine if available (commonly JS/Nashorn or other).
     * Returns stringified result or error message.
     * Example: io.eval("1+2")
     */
    public static String io_eval(String script) {
        try {
            ScriptEngineManager mgr = new ScriptEngineManager();
            // Prefer "js" engines
            ScriptEngine engine = mgr.getEngineByName("js");
            if (engine == null) engine = mgr.getEngineByName("JavaScript");
            if (engine == null) return "No script engine available";
            Object res = engine.eval(script);
            return String.valueOf(res);
        } catch (ScriptException se) {
            return "Script error: " + se.getMessage();
        } catch (Exception e) {
            return "Eval error: " + e.getMessage();
        }
    }

    /* ---------------- Minecraft / Forge integration (best-effort via reflection) ---------------- */

    // These function references will be set if Minecraft/Forge classes detected; otherwise they remain simulation fallbacks.
    private static ChatSender CHAT_SENDER = null;
    static {
        CHAT_SENDER = tryCreateMinecraftChatSender();
    }

    /**
     * Generic interface used internally to send chat messages.
     */
    private interface ChatSender {
        /**
         * send chat message.
         * @param message the string message
         * @param system if true try to send as system message (server or system), otherwise client chat
         */
        void send(String message, boolean system);
    }

    private static ChatSender tryCreateMinecraftChatSender() {
        // Attempt client-side: Minecraft.getInstance().getPlayer().displayClientMessage(Component, boolean)
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = mcClass.getMethod("getInstance");
            Object mcInstance = getInstance.invoke(null);
            Method getPlayer = null;
            try { getPlayer = mcClass.getMethod("player"); } catch (NoSuchMethodException ignore) {}
            if (getPlayer == null) {
                try { getPlayer = mcClass.getMethod("getPlayer"); } catch (NoSuchMethodException ignore2) {}
            }
            if (getPlayer != null) {
                // find Component class
                Class<?> componentCls = Class.forName("net.minecraft.network.chat.Component");
                Method literal = componentCls.getMethod("literal", String.class);
                // try client LocalPlayer.displayClientMessage(Component, boolean)
                Class<?> localPlayerCls = Class.forName("net.minecraft.client.player.LocalPlayer");
                Method displayClientMessage = null;
                try { displayClientMessage = localPlayerCls.getMethod("displayClientMessage", componentCls, boolean.class); } catch (NoSuchMethodException ignore) {}
                if (displayClientMessage != null) {
                    Method finalGetPlayer = getPlayer;
                    Method finalDisplayClientMessage = displayClientMessage;
                    return (message, system) -> {
                        try {
                            Object mc = getInstance.invoke(null);
                            Object player = finalGetPlayer.invoke(mc);
                            Object comp = literal.invoke(null, message);
                            finalDisplayClientMessage.invoke(player, comp, false); // false: add to chat
                        } catch (Throwable t) {
                            System.out.println("[lua.chat] client display failed: " + t.getMessage());
                            System.out.println(message);
                        }
                    };
                }
            }
        } catch (ClassNotFoundException e) {
            // client not present — that's fine, try server-side below
        } catch (Throwable t) {
            System.out.println("[lua.chat] client reflection failed: " + t.getMessage());
        }

        // Attempt server-side: try to find a Server and broadcast a system message
        try {
            // Typical server-side class to build components: net.minecraft.network.chat.Component.literal(...)
            Class<?> componentCls = Class.forName("net.minecraft.network.chat.Component");
            Method literal = componentCls.getMethod("literal", String.class);

            // Try ServerPlayer.sendSystemMessage(Component, UUID)
            Class<?> serverPlayerCls = null;
            try {
                serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            } catch (ClassNotFoundException ignored) {}

            if (serverPlayerCls != null) {
                // find a simple way to broadcast to all players via server.getPlayerList().broadcastChatMessage(...) or server.getPlayerList().broadcastSystemMessage(...)
                // There are multiple possible method names/params across versions; attempt a few options:
                Class<?> minecraftServerCls = Class.forName("net.minecraft.server.MinecraftServer");
                // try to get server from somewhere: net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() (Forge helper)
                try {
                    Class<?> lifecycle = Class.forName("net.minecraftforge.server.ServerLifecycleHooks");
                    Method getCurrentServer = lifecycle.getMethod("getCurrentServer");
                    Object server = getCurrentServer.invoke(null);
                    if (server != null) {
                        // try broadcastSystemMessage(Component, UUID)
                        try {
                            Method broadcast = server.getClass().getMethod("broadcastSystemMessage", componentCls, java.util.UUID.class);
                            return (message, system) -> {
                                try {
                                    Object comp = literal.invoke(null, message);
                                    // broadcast to all players with null UUID (some versions accept null)
                                    broadcast.invoke(server, comp, null);
                                } catch (Throwable t) {
                                    System.out.println("[lua.chat] server broadcast failed: " + t.getMessage());
                                    System.out.println(message);
                                }
                            };
                        } catch (NoSuchMethodException ignore) {
                            // fallback: try player list methods
                            try {
                                Method getPlayerList = server.getClass().getMethod("getPlayerList");
                                Object playerList = getPlayerList.invoke(server);
                                // many versions have broadcastSystemMessage on playerList
                                try {
                                    Method broadcastSystemMessage = playerList.getClass().getMethod("broadcastSystemMessage", componentCls, java.util.UUID.class);
                                    return (message, system) -> {
                                        try {
                                            Object comp = literal.invoke(null, message);
                                            broadcastSystemMessage.invoke(playerList, comp, null);
                                        } catch (Throwable t) {
                                            System.out.println("[lua.chat] playerList broadcast failed: " + t.getMessage());
                                            System.out.println(message);
                                        }
                                    };
                                } catch (NoSuchMethodException ignore2) {}
                            } catch (NoSuchMethodException ignore3) {}
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // Forge ServerLifecycleHooks not present — maybe plain server. Try to find any running server via common classes (best effort)
                    try {
                        Method getServer = minecraftServerCls.getMethod("getServer");
                        Object server = getServer.invoke(null);
                        if (server != null) {
                            Method getPlayerList = server.getClass().getMethod("getPlayerList");
                            Object playerList = getPlayerList.invoke(server);
                            try {
                                Method broadcastSystemMessage = playerList.getClass().getMethod("broadcastSystemMessage", componentCls, java.util.UUID.class);
                                return (message, system) -> {
                                    try {
                                        Object comp = literal.invoke(null, message);
                                        broadcastSystemMessage.invoke(playerList, comp, null);
                                    } catch (Throwable t) {
                                        System.out.println("[lua.chat] fallback server broadcast failed: " + t.getMessage());
                                        System.out.println(message);
                                    }
                                };
                            } catch (NoSuchMethodException ignore2) {}
                        }
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore3) {}
                }
            }
        } catch (ClassNotFoundException e) {
            // server classes not found either
        } catch (Throwable t) {
            System.out.println("[lua.chat] server reflection failed: " + t.getMessage());
        }

        // If all reflection attempts fail, return a simulation sender that prints to stdout.
        return (message, system) -> {
            if (system) {
                System.out.println("[SYSTEM] " + message);
            } else {
                System.out.println("[CHAT] " + message);
            }
        };
    }

    /**
     * Lua-style chat helper.
     * - chat("hi") => posts to game chat if running inside MC/Forge, otherwise prints.
     * - systemChat("server restart") => attempts to send system-level message.
     */
    public static void chat(String message) { chat(message, false); }
    public static void systemChat(String message) { chat(message, true); }
    public static void chat(String message, boolean system) {
        try {
            if (CHAT_SENDER != null) CHAT_SENDER.send(message, system);
            else System.out.println("[CHAT] " + message);
        } catch (Throwable t) {
            System.out.println("[lua.chat] fallback log: " + message);
        }
    }

    /* ---------------- Convenience lua-style wrappers ---------------- */

    // io table
    public static class io {
        public static ProcessResult run(String cmd) { return lua.io_run(cmd); }
        public static String eval(String script) { return lua.io_eval(script); }
        // Convenience: run and print result
        public static void runAndPrint(String cmd) {
            ProcessResult pr = io_run(cmd);
            System.out.println(pr.stdout);
            if (!pr.stderr.isEmpty()) System.err.println(pr.stderr);
        }
    }

    // Expose chat functions under 'chat' table/shortcuts
    public static class env {
        public static void chat(String msg) { lua.chat(msg); }
        public static void system(String msg) { lua.systemChat(msg); }
    }

    /* ---------------- Example helpers and documentation ---------------- */

    /*
     * Example usage:
     *
     * import static libraries.lua.com.lua.*;
     *
     * public class Test {
     *   public static void main(String[] args) {
     *     print("Hello from Java-Lua lib");
     *
     *     // io.run
     *     ProcessResult res = io.run("echo hello world");
     *     print("Exit:", res.exitCode, "Out:", res.stdout);
     *
     *     // io.eval (JS engine)
     *     print("Eval:", io.eval("1+2"));
     *
     *     // chat (will try Minecraft/Forge, otherwise prints)
     *     chat("hello players!");
     *     systemChat("Server will restart in 5 minutes");
     *
     *     // use env.chat for namespacing if you prefer:
     *     env.chat("hello again");
     *   }
     * }
     *
     * Notes:
     * - To actually call the real Forge/Minecraft internals you should build your mod with Forge dependencies.
     *   This class uses reflection so it compiles outside of Minecraft but attempts to use the runtime API when available.
     * - Different Minecraft versions and mappings have slightly different method names and signatures;
     *   the reflection tries multiple common options but may need tweaks for your specific environment/mappings.
     * - If you want explicit Forge/MC bindings (with compile-time type-safety), create a separate module that depends on Forge
     *   and expose direct wrappers wired to this library at runtime.
     */
}

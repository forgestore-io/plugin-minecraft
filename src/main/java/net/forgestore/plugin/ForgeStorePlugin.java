package net.forgestore.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class ForgeStorePlugin extends JavaPlugin {

    private static final String API_BASE = "https://forgestore.net/api/plugin";
    private String secretKey = "";
    private int taskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        secretKey = getConfig().getString("secret_key", "");
        if (secretKey.isEmpty()) {
            getLogger().warning("[ForgeStore] Secret key not set! Run: /forgestore secret YOUR_KEY");
        } else {
            startPolling();
        }
        getLogger().info("[ForgeStore] Plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void startPolling() {
        int ticks = getConfig().getInt("poll_interval_seconds", 30) * 20;
        taskId = Bukkit.getScheduler()
            .runTaskTimerAsynchronously(this, this::pollQueue, 100L, ticks)
            .getTaskId();
        getLogger().info("[ForgeStore] Polling every " + (ticks / 20) + "s");
    }

    private void pollQueue() {
        try {
            URL url = new URL(API_BASE + "/queue?secret=" + secretKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ForgeStore-Minecraft/1.0.0");
            if (conn.getResponseCode() != 200) return;

            String body = readStream(conn.getInputStream());
            if (body.equals("[]") || body.isEmpty()) return;

            List<Map<String, String>> cmds = parseQueue(body);
            getLogger().info("[ForgeStore] Executing " + cmds.size() + " command(s)");

            for (Map<String, String> cmd : cmds) {
                String command = cmd.get("command")
                    .replace("{player}", cmd.getOrDefault("player", ""))
                    .replace("{name}",   cmd.getOrDefault("player", ""))
                    .replace("{uuid}",   cmd.getOrDefault("uuid",   ""))
                    .replace("{amount}", cmd.getOrDefault("amount", ""));
                int id = Integer.parseInt(cmd.getOrDefault("id", "0"));

                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    getLogger().info("[ForgeStore] Done: " + command);
                    markDone(id);
                });
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "[ForgeStore] Poll error: " + e.getMessage());
        }
    }

    private void markDone(int id) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(API_BASE + "/queue");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("DELETE");
                c.setDoOutput(true);
                c.setConnectTimeout(5000);
                c.setRequestProperty("Content-Type", "application/json");
                c.getOutputStream().write(("{\"id\":" + id + ",\"secret\":\"" + secretKey + "\"}").getBytes(StandardCharsets.UTF_8));
                c.getResponseCode();
            } catch (Exception ignored) {}
        });
    }

    private List<Map<String, String>> parseQueue(String json) {
        List<Map<String, String>> list = new ArrayList<>();
        // Simple parser — no external deps
        json = json.trim().replaceAll("^\\[|\\]$", "");
        for (String obj : json.split("\\},\\s*\\{")) {
            Map<String, String> map = new HashMap<>();
            obj = obj.replaceAll("[\\[\\]{}]", "");
            for (String pair : obj.split(",(?=\")")) {
                String[] kv = pair.split("\":\"?", 2);
                if (kv.length == 2) {
                    map.put(kv[0].replaceAll("\"", "").trim(),
                            kv[1].replaceAll("\"\\s*$", "").trim());
                }
            }
            if (map.containsKey("command")) list.add(map);
        }
        return list;
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("forgestore") || args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "secret":
                if (args.length < 2) { sender.sendMessage("§cUsage: /forgestore secret <key>"); return true; }
                secretKey = args[1];
                getConfig().set("secret_key", secretKey);
                saveConfig();
                if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
                startPolling();
                sender.sendMessage("§a[ForgeStore] Key saved and polling started!");
                return true;
            case "check":
                Bukkit.getScheduler().runTaskAsynchronously(this, this::pollQueue);
                sender.sendMessage("§6[ForgeStore] Checking queue...");
                return true;
            case "info":
                sender.sendMessage("§6[ForgeStore] Key: " + (secretKey.isEmpty() ? "§cnot set" : "§aset"));
                sender.sendMessage("§6[ForgeStore] Polling: " + (taskId != -1 ? "§aactive" : "§cinactive"));
                return true;
            case "reload":
                reloadConfig();
                secretKey = getConfig().getString("secret_key", "");
                if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
                if (!secretKey.isEmpty()) startPolling();
                sender.sendMessage("§a[ForgeStore] Reloaded.");
                return true;
        }
        return false;
    }
}

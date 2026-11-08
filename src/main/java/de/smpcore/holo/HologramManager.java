package de.smpcore.holo;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Locs;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/** Leaderboard-Hologramme (TextDisplay). Aktualisierung alle X Minuten (holograms.update-minutes, Standard 30). */
public final class HologramManager {
    public static final List<String> TYPES = List.of("money", "stars", "gems", "kills", "playtime", "clans", "bounty");

    private final SMPCore plugin;
    private final File file;
    private final Map<String, String[]> holos = new LinkedHashMap<>(); // id -> {type, location}
    private final Map<String, UUID> spawned = new HashMap<>();
    private final Map<String, String> text = new HashMap<>();
    private BukkitTask updateTask, ensureTask;

    public HologramManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public void load() {
        holos.clear();
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("holograms");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c != null) holos.put(id, new String[]{c.getString("type", "money"), c.getString("loc", "")});
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        holos.forEach((id, v) -> { y.set("holograms." + id + ".type", v[0]); y.set("holograms." + id + ".loc", v[1]); });
        Persist.write(plugin, file, y.saveToString(), true);
    }

    public int minutes() { return Math.max(1, plugin.getConfig().getInt("holograms.update-minutes", 30)); }

    public void start() {
        stop();
        refreshAll();
        ensureTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> ensure(), 100L, 100L);
        long ticks = 20L * 60 * minutes();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> refreshAll(), ticks, ticks);
    }

    public void stop() {
        if (updateTask != null) updateTask.cancel();
        if (ensureTask != null) ensureTask.cancel();
        for (UUID u : spawned.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        spawned.clear();
    }

    public Collection<String> ids() { return holos.keySet(); }
    public String type(String id) { return holos.get(id)[0]; }

    public boolean create(String id, String type, Location l) {
        if (holos.containsKey(id)) return false;
        holos.put(id, new String[]{type, Locs.str(l)});
        save();
        refreshAll();
        ensure();
        return true;
    }

    public boolean remove(String id) {
        if (holos.remove(id) == null) return false;
        UUID u = spawned.remove(id);
        if (u != null) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        save();
        return true;
    }

    public boolean move(String id, Location l) {
        String[] h = holos.get(id);
        if (h == null) return false;
        h[1] = Locs.str(l);
        UUID u = spawned.remove(id);
        if (u != null) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        save();
        ensure();
        return true;
    }

    public void refreshAll() {
        for (String id : holos.keySet()) {
            text.put(id, build(holos.get(id)[0]));
            UUID u = spawned.get(id);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e instanceof TextDisplay td && td.isValid()) td.text(Msg.c(text.get(id)));
        }
    }

    private void ensure() {
        for (Map.Entry<String, String[]> en : holos.entrySet()) {
            String id = en.getKey();
            Location l = Locs.parse(en.getValue()[1]);
            if (l == null || l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            UUID u = spawned.get(id);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null && e.isValid()) continue;
            TextDisplay td = l.getWorld().spawn(l, TextDisplay.class);
            td.text(Msg.c(text.getOrDefault(id, "...")));
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            spawned.put(id, td.getUniqueId());
        }
    }

    private static String row(int i, String name, String value) {
        return "<gray>" + i + ". <white>" + Msg.esc(name) + " <dark_gray>» " + value;
    }

    private String build(String type) {
        String title;
        List<String> rows = new ArrayList<>();
        int i = 1;
        switch (type) {
            case "stars" -> { title = "<yellow><bold>Meiste Stars"; for (PlayerData d : plugin.economy().top("crystals", 10)) rows.add(row(i++, d.name, "<yellow>" + Num.format(d.crystals))); }
            case "gems" -> { title = "<light_purple><bold>Meiste Gems"; for (PlayerData d : plugin.economy().top("gems", 10)) rows.add(row(i++, d.name, "<light_purple>" + Num.format(d.gems))); }
            case "kills" -> { title = "<red><bold>Meiste Kills"; for (PlayerData d : plugin.economy().top("kills", 10)) rows.add(row(i++, d.name, "<red>" + d.kills)); }
            case "playtime" -> { title = "<green><bold>Meiste Spielzeit"; for (PlayerData d : plugin.economy().top("playtime", 10)) rows.add(row(i++, d.name, "<green>" + Num.duration(d.playtime * 1000))); }
            case "clans" -> {
                title = "<gold><bold>Top Clans";
                for (ClanManager.Clan c : plugin.clans().top()) { if (i > 10) break; rows.add(row(i++, c.name + " [" + c.tag + "]", "<red>" + c.kills + " Kills")); }
            }
            case "bounty" -> {
                title = "<dark_red><bold>Höchste Kopfgelder";
                List<Map.Entry<UUID, Double>> l = new ArrayList<>(plugin.bounties().all().entrySet());
                l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                for (Map.Entry<UUID, Double> e : l) {
                    if (i > 10) break;
                    PlayerData d = plugin.data().get(e.getKey());
                    rows.add(row(i++, d == null ? "?" : d.name, "<green>" + EconomyManager.fmt(e.getValue())));
                }
            }
            default -> { title = "<gold><bold>Reichste Spieler"; for (PlayerData d : plugin.economy().top("money", 10)) rows.add(row(i++, d.name, "<green>" + EconomyManager.fmt(d.money))); }
        }
        while (rows.size() < 10) { rows.add("<gray>" + (rows.size() + 1) + ". <dark_gray>---"); }
        return title + "\n<dark_gray>――――――――――――――\n" + String.join("\n", rows)
                + "\n<dark_gray>――――――――――――――\n<gray>Aktualisiert alle " + minutes() + " Minuten";
    }
}

package de.smpcore.battlepass;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/** Battle Pass: Quests (täglich/wöchentlich) geben Pass-XP, Level schalten Belohnungen frei (Gratis- und Premium-Pass). */
public final class BattlePassManager {
    public static final class Quest {
        public final String id, name, type;
        public final boolean weekly;
        public final Set<Material> materials = new HashSet<>();
        public final long amount;
        public final int xp;
        Quest(String id, String name, String type, boolean weekly, long amount, int xp) {
            this.id = id; this.name = name; this.type = type; this.weekly = weekly; this.amount = amount; this.xp = xp;
        }
        public String key() { return (weekly ? "w_" : "d_") + id; }
    }

    public static final class PData {
        public int xp;
        public boolean premium;
        public final Set<Integer> freeClaimed = new HashSet<>(), premClaimed = new HashSet<>();
        public long day = -1, week = -1;
        public final Map<String, Long> progress = new HashMap<>();
        public final Set<String> done = new HashSet<>();
    }

    public static final class Bundle {
        public long money, stars, gems;
        public final Map<String, Integer> keys = new LinkedHashMap<>();
        public final List<String> commands = new ArrayList<>(), labels = new ArrayList<>();

        public boolean isEmpty() { return money == 0 && stars == 0 && gems == 0 && keys.isEmpty() && commands.isEmpty(); }

        public List<String> describe() {
            List<String> l = new ArrayList<>();
            if (money > 0) l.add("<green>" + EconomyManager.fmt(money));
            if (stars > 0) l.add("<yellow>" + Num.format(stars) + " Stars");
            if (gems > 0) l.add("<light_purple>" + Num.format(gems) + " Gems");
            keys.forEach((k, n) -> l.add("<aqua>" + n + "x " + k + "-Key"));
            for (String s : labels) l.add("<white>" + s);
            return l;
        }
    }

    private final SMPCore plugin;
    private final File dataFile;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final List<Quest> daily = new ArrayList<>(), weekly = new ArrayList<>();
    private final Map<UUID, PData> players = new HashMap<>();

    public BattlePassManager(SMPCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "battlepass-data.yml");
    }

    // ---------------------------------------------------------- Laden / Speichern
    public void loadConfig() {
        cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "battlepass.yml"));
        daily.clear();
        weekly.clear();
        readQuests("quests.daily", false, daily);
        readQuests("quests.weekly", true, weekly);
    }

    private void readQuests(String path, boolean wk, List<Quest> out) {
        for (Map<?, ?> m : cfg.getMapList(path)) {
            Object id = m.get("id"), type = m.get("type");
            if (id == null || type == null) continue;
            long amount = m.get("amount") instanceof Number n ? n.longValue() : 1;
            int xp = m.get("xp") instanceof Number n ? n.intValue() : 100;
            Quest q = new Quest(String.valueOf(id), m.get("name") == null ? String.valueOf(id) : String.valueOf(m.get("name")),
                    String.valueOf(type).toUpperCase(Locale.ROOT), wk, amount, xp);
            if (m.get("materials") instanceof List<?> l) {
                for (Object o : l) {
                    Material mat = Material.matchMaterial(String.valueOf(o));
                    if (mat != null) q.materials.add(mat);
                }
            }
            out.add(q);
        }
    }

    public void loadData() {
        players.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(dataFile).getConfigurationSection("players");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                PData d = new PData();
                d.xp = c.getInt("xp");
                d.premium = c.getBoolean("premium");
                d.freeClaimed.addAll(c.getIntegerList("free"));
                d.premClaimed.addAll(c.getIntegerList("prem"));
                d.day = c.getLong("day", -1);
                d.week = c.getLong("week", -1);
                ConfigurationSection pr = c.getConfigurationSection("progress");
                if (pr != null) for (String q : pr.getKeys(false)) d.progress.put(q, pr.getLong(q));
                d.done.addAll(c.getStringList("done"));
                players.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PData> e : players.entrySet()) {
            String b = "players." + e.getKey() + ".";
            PData d = e.getValue();
            y.set(b + "xp", d.xp);
            y.set(b + "premium", d.premium);
            y.set(b + "free", new ArrayList<>(d.freeClaimed));
            y.set(b + "prem", new ArrayList<>(d.premClaimed));
            y.set(b + "day", d.day);
            y.set(b + "week", d.week);
            d.progress.forEach((q, n) -> y.set(b + "progress." + q, n));
            y.set(b + "done", new ArrayList<>(d.done));
        }
        Persist.write(plugin, dataFile, y.saveToString(), async);
    }

    // ---------------------------------------------------------- Einstellungen
    public boolean enabled() { return cfg.getBoolean("enabled", true); }
    public int maxLevel() { return Math.max(1, cfg.getInt("max-level", 30)); }
    public int xpPerLevel() { return Math.max(1, cfg.getInt("xp-per-level", 1000)); }
    public long premiumCost() { return cfg.getLong("premium-cost-gems", 500); }
    public int xpBonus() { return cfg.getInt("premium-xp-bonus", 20); }
    public String season() { return cfg.getString("season", "Season 1"); }

    public PData pd(Player p) {
        PData d = players.computeIfAbsent(p.getUniqueId(), k -> new PData());
        long today = LocalDate.now().toEpochDay();
        if (d.day != today) {
            d.progress.keySet().removeIf(k -> k.startsWith("d_"));
            d.done.removeIf(k -> k.startsWith("d_"));
            d.day = today;
        }
        if (d.week != today / 7) {
            d.progress.keySet().removeIf(k -> k.startsWith("w_"));
            d.done.removeIf(k -> k.startsWith("w_"));
            d.week = today / 7;
        }
        return d;
    }

    public boolean hasPremium(Player p) { return pd(p).premium || p.hasPermission("smpcore.battlepass.premium"); }
    public int level(PData d) { return Math.min(maxLevel(), d.xp / xpPerLevel()); }

    private List<Quest> pick(List<Quest> pool, long seed, int n) {
        List<Quest> c = new ArrayList<>(pool);
        Collections.shuffle(c, new Random(seed));
        return c.subList(0, Math.min(n, c.size()));
    }

    public List<Quest> activeDaily() { return pick(daily, LocalDate.now().toEpochDay(), cfg.getInt("daily-quests", 3)); }
    public List<Quest> activeWeekly() { return pick(weekly, LocalDate.now().toEpochDay() / 7 + 100000, cfg.getInt("weekly-quests", 3)); }

    // ---------------------------------------------------------- Fortschritt
    public void addProgress(Player p, String type, Material m, long amount) {
        if (!enabled() || amount <= 0) return;
        PData d = pd(p);
        List<Quest> all = new ArrayList<>(activeDaily());
        all.addAll(activeWeekly());
        for (Quest q : all) {
            if (!q.type.equals(type) || d.done.contains(q.key())) continue;
            if (!q.materials.isEmpty() && (m == null || !q.materials.contains(m))) continue;
            long now = d.progress.merge(q.key(), amount, Long::sum);
            if (now >= q.amount) {
                d.done.add(q.key());
                int xp = q.xp * (100 + (hasPremium(p) ? cfg.getInt("premium-xp-bonus", 20) : 0)) / 100;
                String prev = Msg.current();
                Msg.use("pass");
                try { Msg.send(p, "<green>Quest geschafft: <white>" + Msg.esc(q.name) + " <gray>(+" + xp + " Pass-XP)"); } finally { Msg.use(prev); }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
                addXp(p, xp);
            }
        }
    }

    public void addXp(Player p, int xp) {
        PData d = pd(p);
        int before = level(d);
        d.xp += xp;
        int after = level(d);
        if (after > before) {
            String prev = Msg.current();
            Msg.use("pass");
            try { Msg.send(p, "<gold><bold>Battle-Pass Level " + after + "! <gray>Belohnung im <white>/bp <gray>abholen."); } finally { Msg.use(prev); }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    // ---------------------------------------------------------- Belohnungen
    public Bundle bundle(int level, boolean premium) {
        Bundle b = new Bundle();
        for (Map<?, ?> r : cfg.getMapList("rewards." + (premium ? "premium" : "free"))) {
            int every = r.get("every") instanceof Number n ? n.intValue() : 0;
            int at = r.get("at") instanceof Number n ? n.intValue() : 0;
            boolean hit = at > 0 ? at == level : (every > 0 && level % every == 0);
            if (!hit) continue;
            if (r.get("money") instanceof Number n) b.money += n.longValue();
            if (r.get("stars") instanceof Number n) b.stars += n.longValue();
            if (r.get("gems") instanceof Number n) b.gems += n.longValue();
            if (r.get("key") != null) b.keys.merge(String.valueOf(r.get("key")), r.get("amount") instanceof Number n ? n.intValue() : 1, Integer::sum);
            if (r.get("command") != null) {
                b.commands.add(String.valueOf(r.get("command")));
                b.labels.add(r.get("label") == null ? "Spezial-Belohnung" : String.valueOf(r.get("label")));
            }
        }
        return b;
    }

    private void give(Player p, Bundle b) {
        if (b.money > 0) plugin.economy().add(p.getUniqueId(), b.money);
        if (b.stars > 0) plugin.economy().addCrystals(p.getUniqueId(), b.stars);
        if (b.gems > 0) plugin.economy().addGems(p.getUniqueId(), b.gems);
        b.keys.forEach((k, n) -> plugin.crates().addKeys(p.getUniqueId(), k, n));
        for (String c : b.commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
    }

    public void claim(Player p, int level) {
        PData d = pd(p);
        if (level > level(d)) { Msg.error(p, "Dieses Level hast du noch nicht erreicht."); return; }
        boolean any = false;
        Bundle f = bundle(level, false);
        if (!f.isEmpty() && d.freeClaimed.add(level)) { give(p, f); any = true; }
        if (hasPremium(p)) {
            Bundle pr = bundle(level, true);
            if (!pr.isEmpty() && d.premClaimed.add(level)) { give(p, pr); any = true; }
        }
        if (any) { Msg.send(p, "<green>Belohnung von Level " + level + " abgeholt!"); p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f); }
        else Msg.error(p, "Hier gibt es nichts abzuholen.");
    }

    public void buyPremium(Player p) {
        PData d = pd(p);
        if (hasPremium(p)) { Msg.error(p, "Du hast den Premium-Pass schon."); return; }
        if (!plugin.economy().takeGems(p.getUniqueId(), premiumCost())) { Msg.error(p, "Dafür brauchst du " + premiumCost() + " Gems."); return; }
        d.premium = true;
        Msg.send(p, "<gold><bold>Premium-Pass freigeschaltet!");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}

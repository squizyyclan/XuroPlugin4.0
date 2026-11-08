package de.smpcore.farm;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.util.*;

/**
 * Farm-Modus (/farmmode): Stein gibt XP, Erze geben Stars. Vom Spieler gesetzte Blöcke zählen nie (Anti-Exploit).
 * Meldet außerdem abgebaute Blöcke an den Battle Pass.
 */
public final class FarmMode implements Listener {
    private final SMPCore plugin;
    private final Set<UUID> farmers = new HashSet<>();
    private final Set<String> placed = new HashSet<>();
    private final Map<UUID, int[]> daily = new HashMap<>();

    public FarmMode(SMPCore plugin) { this.plugin = plugin; }

    public boolean isOn(UUID id) { return farmers.contains(id); }

    public boolean toggle(Player p) {
        if (!farmers.remove(p.getUniqueId())) { farmers.add(p.getUniqueId()); return true; }
        return false;
    }

    private static String key(Block b) { return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ(); }

    private int oreStars(Material m) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("farmmode.ores");
        if (s == null) return 0;
        String n = m.name();
        if (s.contains(n)) return s.getInt(n);
        String base = n.startsWith("DEEPSLATE_") ? n.substring(10) : n;
        if (base.endsWith("_ORE")) {
            String k = base.substring(0, base.length() - 4);
            if (s.contains(k)) return s.getInt(k);
        }
        return 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (placed.size() > 50_000) placed.clear();
        placed.add(key(e.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        boolean natural = !placed.remove(key(b));
        if (!natural || p.getGameMode() != GameMode.SURVIVAL) return;
        Material m = b.getType();
        plugin.battlePass().addProgress(p, "BREAK", m, 1);
        if (!plugin.getConfig().getBoolean("farmmode.enabled", true) || !farmers.contains(p.getUniqueId())) return;
        int stars = oreStars(m);
        if (stars > 0) {
            int max = plugin.getConfig().getInt("farmmode.max-stars-per-day", 3000);
            int today = (int) LocalDate.now().toEpochDay();
            int[] d = daily.computeIfAbsent(p.getUniqueId(), k -> new int[2]);
            if (d[0] != today) { d[0] = today; d[1] = 0; }
            if (d[1] + stars > max) {
                p.sendActionBar(Msg.c("<red>Farm-Modus: Tageslimit an Stars erreicht (" + max + ")"));
                return;
            }
            d[1] += stars;
            plugin.economy().addCrystals(p.getUniqueId(), stars);
            p.sendActionBar(Msg.c("<yellow>+" + stars + " Stars <dark_gray>(Farm-Modus)"));
        } else if (plugin.getConfig().getStringList("farmmode.stone").contains(m.name())) {
            int xp = plugin.getConfig().getInt("farmmode.stone-xp", 2);
            if (xp > 0) {
                p.giveExp(xp);
                p.sendActionBar(Msg.c("<green>+" + xp + " XP <dark_gray>(Farm-Modus)"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { farmers.remove(e.getPlayer().getUniqueId()); }
}

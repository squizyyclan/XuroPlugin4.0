package de.smpcore.live;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.*;

/** /live <Link>: roter Punkt neben dem Namen (TAB-Liste + Chat) und Ankündigung mit klickbarem Link. */
public final class LiveManager {
    private final SMPCore plugin;
    private final Map<UUID, String> live = new LinkedHashMap<>();
    private final Map<UUID, Long> lastAnnounce = new HashMap<>();

    public LiveManager(SMPCore plugin) { this.plugin = plugin; }

    public boolean isLive(UUID id) { return live.containsKey(id); }
    public Map<UUID, String> all() { return live; }
    public void stop(UUID id) { live.remove(id); }

    public String suffix(Player p) {
        return live.containsKey(p.getUniqueId()) ? " " + plugin.getConfig().getString("live.dot", "<red>●") : "";
    }

    public boolean validLink(String s) {
        if (s.length() > 200 || !(s.startsWith("https://") || s.startsWith("http://"))) return false;
        for (char c : s.toCharArray()) if (Character.isWhitespace(c) || "<>\"'`".indexOf(c) >= 0) return false;
        try {
            String host = URI.create(s).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String d : plugin.getConfig().getStringList("live.allowed-domains")) {
                String dom = d.toLowerCase(Locale.ROOT);
                if (host.equals(dom) || host.endsWith("." + dom)) return true;
            }
        } catch (IllegalArgumentException ignored) { }
        return false;
    }

    public void start(Player p, String link) {
        live.put(p.getUniqueId(), link);
        long cd = plugin.getConfig().getLong("live.announce-cooldown-minutes", 10) * 60_000L;
        Long last = lastAnnounce.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cd) {
            Msg.send(p, "<green>Live-Status gesetzt <dark_gray>(Ankündigung erst in " + ((cd - (now - last)) / 60000 + 1) + " Min wieder möglich)");
            return;
        }
        lastAnnounce.put(p.getUniqueId(), now);
        Bukkit.broadcast(Msg.c("<dark_gray>[<red>LIVE<dark_gray>] <red>● <white>" + Msg.esc(p.getName()) + " <gray>ist jetzt <red><bold>LIVE<gray>! "
                + "<click:open_url:'" + link + "'><aqua><underlined>[Zuschauen]</underlined></aqua></click> <dark_gray>" + Msg.esc(link)));
    }
}

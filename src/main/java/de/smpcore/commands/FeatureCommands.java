package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.*;

/** live, announce, farmmode, hat, repair, near, seen, rules, wiki */
public final class FeatureCommands implements TabExecutor {
    private final SMPCore plugin;
    private final Map<UUID, Long> repairCd = new HashMap<>();

    public FeatureCommands(SMPCore plugin) { this.plugin = plugin; }

    private boolean can(CommandSender s, String node) {
        return s.hasPermission("smpcore.cmd." + node) || s.hasPermission("smpcore.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        switch (n) {
            case "rules" -> {
                Msg.send(s, "<gold><bold>Server-Regeln");
                for (String l : plugin.getConfig().getStringList("rules")) s.sendMessage(Msg.c(l));
                return true;
            }
            case "wiki" -> {
                String url = plugin.getConfig().getString("links.wiki", "");
                if (url.isBlank()) Msg.error(s, "Es ist noch kein Wiki-Link eingetragen.");
                else s.sendMessage(Msg.c("<gold>Wiki & Regeln: <click:open_url:'" + url + "'><aqua><underlined>" + Msg.esc(url) + "</underlined></aqua></click>"));
                return true;
            }
            case "announce" -> { announce(s, a); return true; }
            case "seen" -> { seen(s, a); return true; }
            default -> { }
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        switch (n) {
            case "live" -> live(p, a);
            case "farmmode" -> {
                if (!plugin.getConfig().getBoolean("farmmode.enabled", true)) { Msg.error(p, "Der Farm-Modus ist deaktiviert."); return true; }
                boolean on = plugin.farm().toggle(p);
                Msg.send(p, "Farm-Modus: " + (on ? "<green>AN <gray>– Stein gibt XP, Erze geben Stars." : "<red>AUS"));
            }
            case "hat" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { Msg.error(p, "Halte ein Item in der Hand."); return true; }
                ItemStack old = p.getInventory().getHelmet();
                p.getInventory().setHelmet(hand.clone());
                p.getInventory().setItemInMainHand(old == null ? null : old);
                Msg.send(p, "<green>Item auf dem Kopf!");
            }
            case "repair" -> repair(p);
            case "near" -> {
                double r = plugin.getConfig().getDouble("near.radius", 100);
                List<String> out = new ArrayList<>();
                for (Entity e : p.getNearbyEntities(r, r, r)) {
                    if (e instanceof Player o && !plugin.vanished().contains(o.getUniqueId()))
                        out.add("<white>" + Msg.esc(o.getName()) + " <dark_gray>(" + (int) o.getLocation().distance(p.getLocation()) + "m)");
                }
                Msg.send(p, out.isEmpty() ? "Niemand in " + (int) r + " Blöcken Nähe." : "In der Nähe: " + String.join("<gray>, ", out));
            }
            default -> { }
        }
        return true;
    }

    private void live(Player p, String[] a) {
        var lm = plugin.live();
        if (a.length > 0 && a[0].equalsIgnoreCase("list")) {
            if (lm.all().isEmpty()) { Msg.send(p, "Gerade ist niemand live."); return; }
            Msg.send(p, "<red>● <white>Live jetzt:");
            for (Map.Entry<UUID, String> e : lm.all().entrySet()) {
                Player o = Bukkit.getPlayer(e.getKey());
                if (o != null) p.sendMessage(Msg.c("<gray>• <white>" + Msg.esc(o.getName()) + " <click:open_url:'" + e.getValue() + "'><aqua>[Zuschauen]</aqua></click>"));
            }
            return;
        }
        if (!can(p, "live")) { Msg.error(p, "Dafür brauchst du einen Creator- oder Team-Rang."); return; }
        if (a.length == 0 || a[0].equalsIgnoreCase("off")) {
            if (lm.isLive(p.getUniqueId())) { lm.stop(p.getUniqueId()); Msg.send(p, "<gray>Live-Status beendet."); }
            else Msg.send(p, "Nutzung: <white>/live <Link> <gray>(Twitch, YouTube, Kick, TikTok) – beenden mit <white>/live off");
            return;
        }
        if (!lm.validLink(a[0])) { Msg.error(p, "Ungültiger Link. Erlaubt: " + String.join(", ", plugin.getConfig().getStringList("live.allowed-domains"))); return; }
        lm.start(p, a[0]);
    }

    private void announce(CommandSender s, String[] a) {
        if (!can(s, "announce")) { Msg.error(s, "Keine Rechte."); return; }
        if (a.length == 0) { Msg.error(s, "Nutzung: /announce <Text>  oder  /announce title <Text>"); return; }
        boolean title = a[0].equalsIgnoreCase("title") && a.length > 1;
        String text = String.join(" ", Arrays.copyOfRange(a, title ? 1 : 0, a.length));
        Bukkit.broadcast(Msg.c(""));
        Bukkit.broadcast(Msg.c("<gradient:#B132E7:#E948FA><strikethrough>                                        </strikethrough></gradient>"));
        Bukkit.broadcast(Msg.c("  <gold><bold>ANKÜNDIGUNG"));
        Bukkit.broadcast(Msg.c("  <white>" + text));
        Bukkit.broadcast(Msg.c("<gradient:#E948FA:#B132E7><strikethrough>                                        </strikethrough></gradient>"));
        Bukkit.broadcast(Msg.c(""));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (title) p.showTitle(Title.title(Msg.c("<gold><bold>ANKÜNDIGUNG"), Msg.c(text),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
    }

    private void repair(Player p) {
        if (!can(p, "repair")) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return; }
        long cd = plugin.getConfig().getLong("repair.cooldown-seconds", 3600) * 1000L;
        Long last = repairCd.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cd && !p.hasPermission("smpcore.admin")) {
            Msg.error(p, "Du kannst wieder in " + Num.duration(cd - (now - last)) + " reparieren.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta meta = hand.getItemMeta();
        if (hand.getType().isAir() || !(meta instanceof Damageable d) || d.getDamage() == 0) { Msg.error(p, "Halte ein beschädigtes Werkzeug oder eine Rüstung in der Hand."); return; }
        d.setDamage(0);
        hand.setItemMeta(d);
        repairCd.put(p.getUniqueId(), now);
        Msg.send(p, "<green>Item repariert!");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
    }

    private void seen(CommandSender s, String[] a) {
        if (a.length == 0) { Msg.error(s, "Nutzung: /seen <Spieler>"); return; }
        PlayerData d = plugin.data().find(a[0]);
        if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        Player on = Bukkit.getPlayer(d.uuid);
        if (on != null && !plugin.vanished().contains(d.uuid)) Msg.send(s, "<white>" + Msg.esc(d.name) + " <green>ist gerade online.");
        else if (d.lastSeen <= 0) Msg.send(s, "<white>" + Msg.esc(d.name) + " <gray>wurde schon länger nicht gesehen.");
        else Msg.send(s, "<white>" + Msg.esc(d.name) + " <gray>war vor <white>" + Num.duration(System.currentTimeMillis() - d.lastSeen) + " <gray>online.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && n.equals("seen")) return Players.names(a[0]);
        if (a.length == 1 && n.equals("live")) return Players.complete(a[0], List.of("off", "list"));
        if (a.length == 1 && n.equals("announce")) return Players.complete(a[0], List.of("title"));
        return List.of();
    }
}

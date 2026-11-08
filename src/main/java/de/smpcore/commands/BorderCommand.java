package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /border [overworld|nether|end] [set|expand|shrink <Größe> [Sekunden] | center | warn <Blöcke> | damage <n>] */
public final class BorderCommand implements TabExecutor {
    private final SMPCore plugin;

    public BorderCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
        if (a.length < 2) {
            for (String dim : List.of("overworld", "nether", "end")) {
                World w = plugin.teleports().rtpWorld(dim);
                if (w != null) {
                    WorldBorder b = w.getWorldBorder();
                    Msg.send(s, "<white>" + dim + " <gray>(" + w.getName() + "): Größe <green>" + Num.format(b.getSize()) + " <gray>Mitte <white>"
                            + b.getCenter().getBlockX() + ", " + b.getCenter().getBlockZ());
                }
            }
            Msg.send(s, "Nutzung: <white>/border <overworld|nether|end> <set|expand|shrink|center|warn|damage> [Wert] [Sekunden]");
            return true;
        }
        String dim = a[0].toLowerCase(Locale.ROOT);
        World w = List.of("overworld", "nether", "end").contains(dim) ? plugin.teleports().rtpWorld(dim) : null;
        if (w == null) { Msg.error(s, "Unbekannte Dimension (overworld, nether, end)."); return true; }
        WorldBorder b = w.getWorldBorder();
        String act = a[1].toLowerCase(Locale.ROOT);
        try {
            double v = a.length > 2 ? Num.parse(a[2]) : 0;
            long secs = a.length > 3 ? Long.parseLong(a[3]) : 0;
            switch (act) {
                case "set" -> b.setSize(Math.max(50, Math.min(59_999_968, v)), secs);
                case "expand" -> b.setSize(Math.min(59_999_968, b.getSize() + v), secs);
                case "shrink" -> b.setSize(Math.max(50, b.getSize() - v), secs);
                case "center" -> {
                    if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
                    b.setCenter(p.getLocation());
                }
                case "warn" -> b.setWarningDistance((int) v);
                case "damage" -> b.setDamageAmount(v);
                default -> { Msg.error(s, "Aktion: set, expand, shrink, center, warn, damage"); return true; }
            }
        } catch (NumberFormatException ex) { Msg.error(s, "Ungültiger Wert."); return true; }
        Msg.ok(s, dim + ": Größe " + Num.format(b.getSize()) + ", Mitte " + b.getCenter().getBlockX() + ", " + b.getCenter().getBlockZ());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], List.of("overworld", "nether", "end"));
        if (a.length == 2) return Players.complete(a[1], List.of("set", "expand", "shrink", "center", "warn", "damage"));
        return List.of();
    }
}

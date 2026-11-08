package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.holo.HologramManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /holo create <id> <typ> | remove <id> | move <id> | list | types | refresh | interval <Minuten> */
public final class HoloCommand implements TabExecutor {
    private final SMPCore plugin;

    public HoloCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
        HologramManager hm = plugin.holograms();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!(s instanceof Player p) || a.length < 3) { Msg.error(s, "Nutzung: /holo create <id> <" + String.join("|", HologramManager.TYPES) + ">"); return true; }
                String type = a[2].toLowerCase(Locale.ROOT);
                if (!HologramManager.TYPES.contains(type) || !a[1].matches("[A-Za-z0-9_-]{1,16}")) { Msg.error(s, "Ungültige ID oder Typ."); return true; }
                if (hm.create(a[1].toLowerCase(Locale.ROOT), type, p.getLocation().add(0, 1.5, 0))) Msg.ok(s, "Hologramm erstellt. Verschieben: /holo move " + a[1]);
                else Msg.error(s, "Diese ID gibt es schon.");
            }
            case "remove" -> Msg.send(s, a.length > 1 && hm.remove(a[1].toLowerCase(Locale.ROOT)) ? "<green>Entfernt." : "<red>Nicht gefunden.");
            case "move" -> {
                if (!(s instanceof Player p) || a.length < 2) { Msg.error(s, "Nutzung: /holo move <id> (an deine Position)"); return true; }
                Msg.send(s, hm.move(a[1].toLowerCase(Locale.ROOT), p.getLocation().add(0, 1.5, 0)) ? "<green>Verschoben." : "<red>Nicht gefunden.");
            }
            case "refresh" -> { hm.refreshAll(); Msg.ok(s, "Leaderboards aktualisiert."); }
            case "types" -> Msg.send(s, "Typen: <white>" + String.join(", ", HologramManager.TYPES));
            case "interval" -> {
                try {
                    int m = Math.max(1, Integer.parseInt(a[1]));
                    plugin.getConfig().set("holograms.update-minutes", m);
                    plugin.saveConfig();
                    hm.start();
                    Msg.ok(s, "Aktualisierung alle " + m + " Minuten.");
                } catch (RuntimeException ex) { Msg.error(s, "Nutzung: /holo interval <Minuten>"); }
            }
            default -> {
                Msg.send(s, "Hologramme (alle " + hm.minutes() + " Min): <white>" + (hm.ids().isEmpty() ? "keine" : String.join(", ", hm.ids())));
                Msg.send(s, "<gray>/holo create|remove|move|list|types|refresh|interval");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], List.of("create", "remove", "move", "list", "types", "refresh", "interval"));
        if (a.length == 2 && List.of("remove", "move").contains(a[0].toLowerCase(Locale.ROOT))) return Players.complete(a[1], new ArrayList<>(plugin.holograms().ids()));
        if (a.length == 3 && a[0].equalsIgnoreCase("create")) return Players.complete(a[2], HologramManager.TYPES);
        return List.of();
    }
}

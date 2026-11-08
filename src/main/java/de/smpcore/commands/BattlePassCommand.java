package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.BattlePassMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /battlepass | /bp quests | /bp premium */
public final class BattlePassCommand implements TabExecutor {
    private final SMPCore plugin;

    public BattlePassCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.battlePass().enabled()) { Msg.error(p, "Der Battle Pass ist derzeit deaktiviert."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("quests")) new BattlePassMenus.Quests(p).open();
        else if (a.length > 0 && a[0].equalsIgnoreCase("premium")) plugin.battlePass().buyPremium(p);
        else new BattlePassMenus.Main(p).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? Players.complete(a[0], List.of("quests", "premium")) : List.of();
    }
}

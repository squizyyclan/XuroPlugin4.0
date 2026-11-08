package de.smpcore.listeners;

import de.smpcore.SMPCore;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/** Quest-Fortschritt für Kills und Angeln (Block-Abbau läuft über FarmMode, Verkauf über ShopManager, Spielzeit über SMPCore). */
public final class BattlePassListener implements Listener {
    private final SMPCore plugin;

    public BattlePassListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        if (e.getEntity() instanceof Monster) plugin.battlePass().addProgress(k, "KILL_MOB", null, 1);
        else if (e.getEntity() instanceof Player v && !v.equals(k)) plugin.battlePass().addProgress(k, "KILL_PLAYER", null, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) plugin.battlePass().addProgress(e.getPlayer(), "FISH", null, 1);
    }
}

package de.smpcore.gui;

import de.smpcore.battlepass.BattlePassManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BattlePassMenus {
    private BattlePassMenus() {}

    private static String bar(long have, long total) {
        int filled = (int) Math.min(10, total <= 0 ? 10 : have * 10 / total);
        return "<green>" + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(10 - filled);
    }

    /** Level-Übersicht: Tap auf ein Level holt die Belohnungen ab. */
    public static final class Main extends PagedMenu {
        public Main(Player v) { super(v, "<gold><bold>Battle Pass"); }

        @Override
        protected List<Entry> entries() {
            BattlePassManager bp = plugin.battlePass();
            BattlePassManager.PData d = bp.pd(viewer);
            boolean prem = bp.hasPremium(viewer);
            int cur = bp.level(d);
            List<Entry> out = new ArrayList<>();
            for (int lvl = 1; lvl <= bp.maxLevel(); lvl++) {
                final int level = lvl;
                BattlePassManager.Bundle f = bp.bundle(lvl, false), p = bp.bundle(lvl, true);
                boolean reached = lvl <= cur;
                boolean freeAvail = reached && !f.isEmpty() && !d.freeClaimed.contains(lvl);
                boolean premAvail = reached && prem && !p.isEmpty() && !d.premClaimed.contains(lvl);
                Material m = freeAvail || premAvail ? Material.CHEST : reached ? Material.LIME_DYE : Material.GRAY_DYE;
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Gratis-Pass:");
                for (String s : f.describe()) lore.add("  " + s);
                if (f.isEmpty()) lore.add("  <dark_gray>–");
                lore.add(prem ? "<gold>Premium-Pass:" : "<gold>Premium-Pass <dark_gray>(gesperrt):");
                for (String s : p.describe()) lore.add("  " + s);
                if (p.isEmpty()) lore.add("  <dark_gray>–");
                lore.add("");
                lore.add(freeAvail || premAvail ? "<yellow>Klicken zum Abholen!" : reached ? "<green>Alles abgeholt" : "<dark_gray>Noch nicht erreicht");
                out.add(new Entry(Items.make(m, Math.min(64, lvl), "<gold>Level " + lvl, lore), e -> { bp.claim(viewer, level); refresh(); }));
            }
            return out;
        }

        @Override
        protected void controls() {
            BattlePassManager bp = plugin.battlePass();
            BattlePassManager.PData d = bp.pd(viewer);
            int cur = bp.level(d);
            long into = cur >= bp.maxLevel() ? bp.xpPerLevel() : d.xp % bp.xpPerLevel();
            set(49, Items.make(Material.EXPERIENCE_BOTTLE, "<gold>" + Msg.esc(bp.season()) + " <gray>– Level <white>" + cur + "<dark_gray>/<white>" + bp.maxLevel(),
                    bar(into, bp.xpPerLevel()), "<gray>XP: <white>" + Num.format(d.xp)));
            set(46, Items.make(Material.WRITABLE_BOOK, "<aqua>Quests", "<gray>Tägliche & wöchentliche Aufgaben", "", "<yellow>Klicken"), e -> go(new Quests(viewer)));
            if (bp.hasPremium(viewer)) {
                set(47, Items.make(Material.NETHER_STAR, "<gold>Premium-Pass aktiv", "<gray>+" + bp.xpBonus() + "% Quest-XP"));
            } else {
                set(47, Items.make(Material.NETHER_STAR, "<gold>Premium-Pass freischalten", "<gray>Zusätzliche Belohnungen & mehr Quest-XP",
                        "<gray>Preis: <light_purple>" + bp.premiumCost() + " Gems", "", "<yellow>Klicken"),
                        e -> go(new ConfirmMenu(viewer, "<gold>Premium-Pass kaufen?", Items.make(Material.NETHER_STAR, "<gold>Premium-Pass", "<light_purple>" + bp.premiumCost() + " Gems"),
                                () -> bp.buyPremium(viewer), () -> new Main(viewer))));
            }
            set(51, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
        }
    }

    public static final class Quests extends Menu {
        public Quests(Player v) { super(v, 36, "<aqua><bold>Battle-Pass-Quests"); }

        private Material icon(String type) {
            return switch (type) {
                case "BREAK" -> Material.DIAMOND_PICKAXE;
                case "KILL_MOB" -> Material.IRON_SWORD;
                case "KILL_PLAYER" -> Material.SKELETON_SKULL;
                case "FISH" -> Material.FISHING_ROD;
                case "PLAYTIME" -> Material.CLOCK;
                case "SELL" -> Material.GOLD_INGOT;
                default -> Material.PAPER;
            };
        }

        private void put(int slot, BattlePassManager.Quest q, BattlePassManager.PData d) {
            long have = Math.min(q.amount, d.progress.getOrDefault(q.key(), 0L));
            boolean done = d.done.contains(q.key());
            String shown = q.type.equals("SELL") ? "$" + Num.format(have) + " / $" + Num.format(q.amount) : Num.format(have) + " / " + Num.format(q.amount);
            set(slot, Items.make(done ? Material.LIME_DYE : icon(q.type), 1, (done ? "<green>✔ " : "<white>") + Msg.esc(q.name),
                    List.of(bar(have, q.amount) + " <gray>" + shown, "<gray>Belohnung: <gold>" + q.xp + " Pass-XP", done ? "<green>Erledigt!" : "<dark_gray>Läuft …")));
        }

        @Override
        protected void build() {
            BattlePassManager bp = plugin.battlePass();
            BattlePassManager.PData d = bp.pd(viewer);
            long hoursToReset = 24 - java.time.LocalTime.now().getHour();
            long daysToReset = 7 - (LocalDate.now().toEpochDay() % 7);
            set(4, Items.make(Material.BOOK, "<gold>Quests", "<gray>Täglich neu in ~" + hoursToReset + " Std.", "<gray>Wöchentlich neu in ~" + daysToReset + " Tagen"));
            set(9, Items.make(Material.SUNFLOWER, "<yellow>Täglich"));
            set(18, Items.make(Material.NETHER_STAR, "<light_purple>Wöchentlich"));
            int s = 10;
            for (BattlePassManager.Quest q : bp.activeDaily()) { if (s > 16) break; put(s++, q, d); }
            s = 19;
            for (BattlePassManager.Quest q : bp.activeWeekly()) { if (s > 25) break; put(s++, q, d); }
            set(31, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer)));
        }
    }
}

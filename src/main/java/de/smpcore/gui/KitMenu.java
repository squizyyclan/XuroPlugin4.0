package de.smpcore.gui;

import de.smpcore.kit.KitManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Kit-Übersicht. Tap auf ein Kit = Vorschau mit allen Items und Abholen-Button. */
public final class KitMenu extends PagedMenu {
    public KitMenu(Player v) { super(v, "<green><bold>Kits"); }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (KitManager.Kit k : plugin.kits().all()) {
            List<String> lore = new ArrayList<>(k.description);
            lore.add("");
            int shown = 0;
            for (ItemStack it : k.items) {
                if (shown >= 6) break;
                String ench = it.getEnchantments().isEmpty() ? "" : " <dark_gray>(" + it.getEnchantments().size() + " Verzauberungen)";
                lore.add("<gray>• <white>" + it.getAmount() + "x " + Items.pretty(it.getType()) + ench);
                shown++;
            }
            if (k.items.size() > shown) lore.add("<gray>… und <white>" + (k.items.size() - shown) + " <gray>weitere");
            lore.add("");
            lore.add("<gray>Status: " + plugin.kits().status(viewer, k));
            lore.add("<yellow>Klicken: Vorschau & Abholen");
            Material icon = plugin.kits().hasAccess(viewer, k) ? k.icon : Material.BARRIER;
            out.add(new Entry(Items.make(icon, 1, k.name, lore), e -> go(new Detail(viewer, k))));
        }
        return out;
    }

    @Override
    protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater()); }

    /** Alle Items eines Kits ansehen und abholen. */
    public static final class Detail extends Menu {
        private final KitManager.Kit kit;

        public Detail(Player v, KitManager.Kit kit) {
            super(v, 54, "<dark_gray>Kit: <white>" + kit.name);
            this.kit = kit;
        }

        @Override
        protected void build() {
            for (int i = 0; i < kit.items.size() && i < 45; i++) set(i, kit.items.get(i).clone());
            long w = plugin.kits().waitMs(viewer, kit);
            boolean ready = plugin.kits().hasAccess(viewer, kit) && w == 0;
            set(49, Items.make(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    ready ? "<green><bold>Kit abholen" : "<red><bold>Nicht verfügbar",
                    "<gray>Status: " + plugin.kits().status(viewer, kit),
                    kit.equip ? "<dark_gray>Rüstung wird automatisch angezogen" : "",
                    "", ready ? "<yellow>Klicken zum Abholen" : ""), e -> { plugin.kits().claim(viewer, kit); refresh(); });
            set(45, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new KitMenu(viewer)));
            for (int i = 45; i < 54; i++) if (i != 49 && i != 45) set(i, Items.filler());
        }
    }
}

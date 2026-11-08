package de.smpcore.kit;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

/**
 * Kits mit Cooldown und Permission (kits.yml). cooldown: -1 = einmalig, 0 = jederzeit.
 * Items: material, amount, name, lore, enchants, unbreakable. Rüstung wird automatisch angezogen, wenn der Slot frei ist (equip: true).
 */
public final class KitManager {
    public static final class Kit {
        public final String id;
        public String name, permission;
        public Material icon = Material.CHEST;
        public long cooldown;
        public boolean equip = true;
        public final List<String> description = new ArrayList<>();
        public final List<ItemStack> items = new ArrayList<>();
        public final List<String> commands = new ArrayList<>();
        public Kit(String id) { this.id = id; }
    }

    private final SMPCore plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        kits.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "kits.yml"));
        ConfigurationSection root = y.getConfigurationSection("kits");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            Kit k = new Kit(id.toLowerCase(Locale.ROOT));
            k.name = c.getString("name", id);
            k.permission = c.getString("permission", "");
            k.cooldown = c.getLong("cooldown", 86400);
            k.equip = c.getBoolean("equip", true);
            k.description.addAll(c.getStringList("description"));
            Material ic = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (ic != null) k.icon = ic;
            for (Map<?, ?> m : c.getMapList("items")) {
                ItemStack it = parseItem(m, k.id);
                if (it != null) k.items.add(it);
            }
            k.commands.addAll(c.getStringList("commands"));
            kits.put(k.id, k);
        }
    }

    private ItemStack parseItem(Map<?, ?> m, String kit) {
        Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("Kit " + kit + ": unbekanntes Item " + m.get("material"));
            return null;
        }
        List<String> black = plugin.getConfig().getStringList("shop-blacklist");
        if (black.contains(mat.name())) {
            plugin.getLogger().warning("Kit " + kit + ": " + mat + " steht auf der Blacklist und wird übersprungen.");
            return null;
        }
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
        ItemStack it = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (m.get("name") != null) meta.displayName(Msg.item(String.valueOf(m.get("name"))));
        if (m.get("lore") instanceof List<?> l) {
            List<Component> lore = new ArrayList<>();
            for (Object o : l) lore.add(Msg.item(String.valueOf(o)));
            meta.lore(lore);
        }
        if (Boolean.TRUE.equals(m.get("unbreakable"))) meta.setUnbreakable(true);
        if (m.get("trim") instanceof Map<?, ?> tm && meta instanceof ArmorMeta am) {
            TrimPattern pat = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(String.valueOf(tm.get("pattern")).toLowerCase(Locale.ROOT)));
            TrimMaterial tmat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(String.valueOf(tm.get("material")).toLowerCase(Locale.ROOT)));
            if (pat != null && tmat != null) am.setTrim(new ArmorTrim(tmat, pat));
            else plugin.getLogger().warning("Kit " + kit + ": unbekannter Trim " + tm);
        }
        if (m.get("effects") instanceof Map<?, ?> fx && meta instanceof PotionMeta pm) {
            for (Map.Entry<?, ?> en : fx.entrySet()) {
                PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(String.valueOf(en.getKey()).toLowerCase(Locale.ROOT)));
                if (t == null || !(en.getValue() instanceof Map<?, ?> v)) { plugin.getLogger().warning("Kit " + kit + ": unbekannter Effekt " + en.getKey()); continue; }
                int secs = v.get("seconds") instanceof Number sn ? sn.intValue() : 60;
                int lvl = v.get("level") instanceof Number ln ? ln.intValue() : 1;
                pm.addCustomEffect(new PotionEffect(t, secs * 20, Math.max(0, lvl - 1)), true);
            }
        }
        if (m.get("enchants") instanceof Map<?, ?> em) {
            for (Map.Entry<?, ?> en : em.entrySet()) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(String.valueOf(en.getKey()).toLowerCase(Locale.ROOT)));
                if (ench == null) {
                    plugin.getLogger().warning("Kit " + kit + ": unbekannte Verzauberung " + en.getKey());
                    continue;
                }
                if (en.getValue() instanceof Number lvl) meta.addEnchant(ench, lvl.intValue(), true);
            }
        }
        it.setItemMeta(meta);
        return it;
    }

    public Collection<Kit> all() { return kits.values(); }
    public Kit get(String id) { return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT)); }

    public boolean hasAccess(Player p, Kit k) {
        return k.permission == null || k.permission.isEmpty() || p.hasPermission(k.permission) || p.hasPermission("smpcore.admin");
    }

    /** Wartezeit in Millisekunden, 0 = bereit, -1 = einmalig schon abgeholt. */
    public long waitMs(Player p, Kit k) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        Long last = d.kitUsed.get(k.id);
        if (last == null) return 0;
        if (k.cooldown < 0) return -1;
        return Math.max(0, last + k.cooldown * 1000 - System.currentTimeMillis());
    }

    public String status(Player p, Kit k) {
        if (!hasAccess(p, k)) return "<red>Gesperrt (Rang erforderlich)";
        long w = waitMs(p, k);
        if (w < 0) return "<red>Bereits abgeholt (einmalig)";
        if (w > 0) return "<yellow>Wieder in " + Num.duration(w);
        return "<green>Bereit";
    }

    private static boolean empty(ItemStack it) { return it == null || it.getType().isAir(); }

    /** Zieht Rüstung an, wenn der Slot frei ist. */
    private boolean equipArmor(Player p, ItemStack it) {
        String n = it.getType().name();
        PlayerInventory inv = p.getInventory();
        if (n.endsWith("_HELMET") && empty(inv.getHelmet())) { inv.setHelmet(it); return true; }
        if (n.endsWith("_CHESTPLATE") && empty(inv.getChestplate())) { inv.setChestplate(it); return true; }
        if (n.endsWith("_LEGGINGS") && empty(inv.getLeggings())) { inv.setLeggings(it); return true; }
        if (n.endsWith("_BOOTS") && empty(inv.getBoots())) { inv.setBoots(it); return true; }
        return false;
    }

    public void claim(Player p, Kit k) {
        if (!hasAccess(p, k)) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return; }
        long w = waitMs(p, k);
        if (w < 0) { Msg.error(p, "Dieses Kit gibt es nur einmal."); return; }
        if (w > 0) { Msg.error(p, "Du kannst dieses Kit in " + Num.duration(w) + " wieder abholen."); return; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        int dropped = 0;
        for (ItemStack it : k.items) {
            ItemStack c = it.clone();
            if (k.equip && equipArmor(p, c)) continue;
            for (ItemStack left : p.getInventory().addItem(c).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
                dropped++;
            }
        }
        for (String cmd : k.commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        d.kitUsed.put(k.id, System.currentTimeMillis());
        Msg.send(p, "<green>Kit abgeholt: <white>" + k.name);
        if (dropped > 0) Msg.send(p, "<yellow>Dein Inventar war voll – " + dropped + " Item(s) liegen am Boden!");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }
}

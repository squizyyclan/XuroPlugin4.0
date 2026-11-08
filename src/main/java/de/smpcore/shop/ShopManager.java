package de.smpcore.shop;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public final class ShopManager {
    public record Item(Material material, double buy, double sell) {}
    public record Category(String id, String name, Material icon, int slot, boolean locked, String action, List<Item> items) {}

    private final SMPCore plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Double> sellPrices = new HashMap<>();

    public ShopManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        categories.clear();
        sellPrices.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "shop.yml"));
        List<String> black = plugin.getConfig().getStringList("shop-blacklist");
        ConfigurationSection cs = y.getConfigurationSection("categories");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            List<Item> items = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("items")) {
                Object mat = m.get("material");
                if (mat == null) continue;
                Material mt = Material.matchMaterial(String.valueOf(mat));
                if (mt == null || !mt.isItem()) {
                    plugin.getLogger().warning("Shop: unbekanntes Item " + mat);
                    continue;
                }
                if (black.contains(mt.name())) { plugin.getLogger().warning("Shop: " + mt + " steht auf der Blacklist und wird übersprungen."); continue; }
                double buy = num(m.get("buy"));
                double sell = num(m.get("sell"));
                items.add(new Item(mt, buy, sell));
                if (sell > 0) sellPrices.merge(mt, sell, Math::max);
            }
            categories.put(id, new Category(id, c.getString("name", id), icon, c.getInt("slot", -1), c.getBoolean("locked", false), c.getString("action", ""), items));
        }
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    public Collection<Category> categories() { return categories.values(); }

    /** Verkaufspreis pro Stück: fester Preis aus shop.yml, sonst automatisch (jedes Item ist verkaufbar). */
    public double sellPrice(Material m) {
        Double explicit = sellPrices.get(m);
        if (explicit != null) return explicit;
        if (!plugin.getConfig().getBoolean("sell.all-items", true)) return 0;
        return autoPrice(m);
    }

    private static final Set<String> NO_SELL = Set.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK", "LIGHT", "SPAWNER", "KNOWLEDGE_BOOK");
    private static final Map<String, Double> SPECIAL = new HashMap<>();
    static {
        Object[] kv = {"ELYTRA", 5000, "TOTEM_OF_UNDYING", 3000, "NETHER_STAR", 3000, "DRAGON_EGG", 20000, "DRAGON_HEAD", 3000, "BEACON", 4000,
                "CONDUIT", 3000, "ENCHANTED_GOLDEN_APPLE", 2000, "HEART_OF_THE_SEA", 1500, "TRIDENT", 1500, "MACE", 3000, "ENDER_CHEST", 400,
                "WITHER_SKELETON_SKULL", 2000, "DIAMOND_BLOCK", 1620, "EMERALD_BLOCK", 1170, "GOLD_BLOCK", 450, "IRON_BLOCK", 225,
                "NETHERITE_BLOCK", 22500, "NETHERITE_INGOT", 2500, "COAL_BLOCK", 72, "REDSTONE_BLOCK", 45, "LAPIS_BLOCK", 54, "COPPER_BLOCK", 54,
                "QUARTZ_BLOCK", 32, "AMETHYST_BLOCK", 40, "RAW_IRON", 20, "RAW_GOLD", 40, "RAW_COPPER", 5, "AMETHYST_SHARD", 10, "ECHO_SHARD", 200,
                "PHANTOM_MEMBRANE", 40, "GHAST_TEAR", 100, "BLAZE_ROD", 40, "ENDER_EYE", 120, "BREEZE_ROD", 80, "HEAVY_CORE", 5000,
                "ENCHANTED_BOOK", 250, "GOLDEN_APPLE", 200, "SHULKER_SHELL", 500, "SHULKER_BOX", 1000, "NAME_TAG", 50, "SADDLE", 150,
                "EXPERIENCE_BOTTLE", 30};
        for (int i = 0; i < kv.length; i += 2) SPECIAL.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
    }

    private double autoPrice(Material m) {
        String n = m.name();
        if (!m.isItem() || m.isAir() || n.startsWith("LEGACY_") || NO_SELL.contains(n) || plugin.getConfig().getStringList("sell.blacklist").contains(n)) return 0;
        double p;
        Double sp = SPECIAL.get(n);
        if (sp != null) p = sp;
        else {
            double tier = tierPrice(n), piece = pieceFactor(n);
            p = tier > 0 && piece > 0 ? Math.max(1, tier * piece * 0.6) : patternPrice(n);
        }
        return Math.round(p * plugin.getConfig().getDouble("sell.multiplier", 1.0) * 100) / 100.0;
    }

    private static double tierPrice(String n) {
        if (n.startsWith("NETHERITE_")) return 2500;
        if (n.startsWith("DIAMOND_")) return 180;
        if (n.startsWith("GOLDEN_")) return 50;
        if (n.startsWith("IRON_")) return 25;
        if (n.startsWith("CHAINMAIL_")) return 20;
        if (n.startsWith("LEATHER_")) return 6;
        if (n.startsWith("STONE_") || n.startsWith("WOODEN_")) return 2;
        return 0;
    }

    private static double pieceFactor(String n) {
        if (n.endsWith("_HELMET")) return 5;
        if (n.endsWith("_CHESTPLATE")) return 8;
        if (n.endsWith("_LEGGINGS")) return 7;
        if (n.endsWith("_BOOTS")) return 4;
        if (n.endsWith("_SWORD")) return 2;
        if (n.endsWith("_PICKAXE")) return 3;
        if (n.endsWith("_AXE")) return 3;
        if (n.endsWith("_SHOVEL")) return 1;
        if (n.endsWith("_HOE")) return 2;
        if (n.endsWith("_HORSE_ARMOR")) return 3;
        return 0;
    }

    private double patternPrice(String n) {
        if (n.endsWith("_SPAWN_EGG")) return 150;
        if (n.startsWith("MUSIC_DISC_")) return 400;
        if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) return 300;
        if (n.endsWith("_SMITHING_TEMPLATE")) return 300;
        if (n.endsWith("_BANNER_PATTERN")) return 100;
        if (n.endsWith("_POTTERY_SHERD")) return 80;
        if (n.contains("POTION")) return 25;
        if (n.equals("TIPPED_ARROW")) return 10;
        if (n.endsWith("_ORE")) {
            if (n.contains("DIAMOND")) return 200;
            if (n.contains("EMERALD")) return 140;
            if (n.contains("GOLD")) return 60;
            if (n.contains("IRON")) return 30;
            if (n.contains("LAPIS")) return 40;
            if (n.contains("REDSTONE")) return 30;
            if (n.contains("COPPER")) return 8;
            if (n.contains("COAL")) return 10;
            return 20;
        }
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT")) return 4;
        if (n.endsWith("_MINECART")) return 30;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 3;
        if (n.endsWith("_PLANKS")) return 1;
        if (n.endsWith("_BED")) return 12;
        if (n.endsWith("_WOOL")) return 2;
        if (n.endsWith("_GLAZED_TERRACOTTA")) return 4;
        if (n.endsWith("_CONCRETE") || n.endsWith("_CONCRETE_POWDER") || n.endsWith("_TERRACOTTA")) return 2;
        if (n.contains("GLASS")) return 2;
        if (n.endsWith("_DYE")) return 2;
        if (n.endsWith("_SAPLING") || n.endsWith("_SEEDS")) return 1;
        if (n.endsWith("_LEAVES")) return 0.5;
        if (n.endsWith("_STAIRS") || n.endsWith("_WALL") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")) return 1;
        if (n.endsWith("_SLAB") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_CARPET")) return 0.5;
        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_SIGN")) return 3;
        if (n.endsWith("_BRICKS") || n.endsWith("_TILES")) return 3;
        if (n.contains("COPPER")) return 20;
        if (n.startsWith("COOKED_") || n.endsWith("_STEW") || n.endsWith("_SOUP") || n.endsWith("_PIE") || n.equals("BREAD") || n.equals("CAKE")) return 10;
        if (List.of("BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "COD", "SALMON", "TROPICAL_FISH").contains(n)) return 4;
        return plugin.getConfig().getDouble("sell.default-price", 1.0);
    }

    /** Item ohne eigenen Namen/Lore/Plugin-Daten (also kein Spezial-Item). Beschädigung und Verzauberungen sind erlaubt. */
    public boolean plain(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (!it.hasItemMeta()) return true;
        ItemMeta m = it.getItemMeta();
        return !m.hasDisplayName() && !m.hasLore() && m.getPersistentDataContainer().getKeys().isEmpty();
    }

    private static boolean damaged(ItemStack it) {
        return it.getItemMeta() instanceof Damageable d && d.getDamage() > 0;
    }

    /** Wert dieses konkreten Stücks (Haltbarkeit senkt, Verzauberungen erhöhen den Preis). */
    public double priceOf(ItemStack it) {
        double p = sellPrice(it.getType());
        if (p <= 0) return 0;
        int max = it.getType().getMaxDurability();
        if (max > 0 && it.getItemMeta() instanceof Damageable d) p *= Math.max(0.1, 1.0 - (double) d.getDamage() / max);
        for (int lvl : it.getEnchantments().values()) p += lvl * 25.0;
        return Math.round(p * 100) / 100.0;
    }

    public boolean canSell(ItemStack it) { return plain(it) && priceOf(it) > 0; }

    /** Ausrüstung/verzauberte Items/Shulker: werden von /sell all und dem Einlege-Button geschützt. */
    public boolean isGear(ItemStack it) {
        return it.getType().getMaxDurability() > 0 || !it.getEnchantments().isEmpty() || it.getType().name().endsWith("SHULKER_BOX");
    }


    private int space(Player p, Material m) {
        int max = m.getMaxStackSize();
        int space = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) space += max;
            else if (it.getType() == m && !it.hasItemMeta()) space += Math.max(0, max - it.getAmount());
        }
        return space;
    }

    public void buy(Player p, Item item, int amount) {
        EconomyManager eco = plugin.economy();
        int can = Math.min(amount, space(p, item.material()));
        if (can <= 0) { Msg.error(p, "Dein Inventar ist voll!"); return; }
        double cost = item.buy() * can;
        if (!eco.take(p.getUniqueId(), cost)) {
            Msg.error(p, "Nicht genug Geld! Du brauchst " + EconomyManager.fmt(cost));
            return;
        }
        p.getInventory().addItem(new ItemStack(item.material(), can));
        Msg.send(p, "Gekauft: <white>" + can + "x " + Items.pretty(item.material()) + " <gray>für <green>" + EconomyManager.fmt(cost));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    public int removeItems(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack s = cont[i];
            if (s == null || s.getType() != m || !plain(s) || damaged(s)) continue;
            int take = Math.min(left, s.getAmount());
            if (take == s.getAmount()) cont[i] = null;
            else s.setAmount(s.getAmount() - take);
            left -= take;
        }
        p.getInventory().setStorageContents(cont);
        return amount - left;
    }

    public void sellItem(Player p, Material m, double price, int amount) {
        int removed = removeItems(p, m, amount);
        if (removed <= 0) { Msg.error(p, "Du hast kein " + Items.pretty(m) + " zum Verkaufen."); return; }
        double total = removed * price;
        plugin.economy().add(p.getUniqueId(), total);
        plugin.battlePass().addProgress(p, "SELL", null, (long) total);
        Msg.send(p, "Verkauft: <white>" + removed + "x " + Items.pretty(m) + " <gray>für <gold>" + EconomyManager.fmt(total));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }

    /** /sell all: verkauft alles Verkaufbare ausser Ausrüstung, verzauberten Items und Shulkern. */
    public void sellAll(Player p) {
        ItemStack[] cont = p.getInventory().getStorageContents();
        double total = 0;
        int items = 0;
        for (int i = 0; i < cont.length; i++) {
            ItemStack s = cont[i];
            if (s == null || !canSell(s) || isGear(s)) continue;
            total += priceOf(s) * s.getAmount();
            items += s.getAmount();
            cont[i] = null;
        }
        if (items == 0) { Msg.error(p, "Du hast nichts Verkaufbares im Inventar. (Ausrüstung und verzauberte Items schützt /sell all – nutze /sell oder /sell hand.)"); return; }
        p.getInventory().setStorageContents(cont);
        total = Math.round(total * 100) / 100.0;
        plugin.economy().add(p.getUniqueId(), total);
        plugin.battlePass().addProgress(p, "SELL", null, (long) total);
        Msg.send(p, "Verkauft: <white>" + items + " Items <gray>für <gold>" + EconomyManager.fmt(total));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }

    public void sellHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!canSell(hand)) { Msg.error(p, "Dieses Item kann nicht verkauft werden."); return; }
        double total = priceOf(hand) * hand.getAmount();
        int n = hand.getAmount();
        Material m = hand.getType();
        p.getInventory().setItemInMainHand(null);
        plugin.economy().add(p.getUniqueId(), total);
        plugin.battlePass().addProgress(p, "SELL", null, (long) total);
        Msg.send(p, "Verkauft: <white>" + n + "x " + Items.pretty(m) + " <gray>für <gold>" + EconomyManager.fmt(total));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }
}

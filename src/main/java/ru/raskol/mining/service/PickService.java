package ru.raskol.mining.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PickTier;

import java.util.ArrayList;
import java.util.List;

/** Создание и распознавание кирок, фрагментов и золотого самородка. */
public final class PickService {

    private final RaskolMining plugin;
    public final NamespacedKey pickTierKey;
    public final NamespacedKey pickFragmentKey;
    public final NamespacedKey goldenNuggetKey;

    public PickService(RaskolMining plugin) {
        this.plugin = plugin;
        this.pickTierKey = new NamespacedKey(plugin, "pick_tier");
        this.pickFragmentKey = new NamespacedKey(plugin, "pick_fragment");
        this.goldenNuggetKey = new NamespacedKey(plugin, "golden_nugget");
    }

    /* ================= Кирки ================= */

    public boolean isPick(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    public PickTier tierOf(ItemStack item) {
        if (!isPick(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return PickTier.VANILLA;
        String tag = meta.getPersistentDataContainer().get(pickTierKey, PersistentDataType.STRING);
        if (tag == null) return PickTier.VANILLA;
        try {
            return PickTier.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return PickTier.VANILLA;
        }
    }

    public boolean isCustomPick(ItemStack item) {
        PickTier tier = tierOf(item);
        return tier != null && tier != PickTier.VANILLA;
    }

    public ItemStack createPick(PickTier tier) {
        Material base = switch (tier) {
            case VETERAN -> Material.IRON_PICKAXE;
            case DWARF -> Material.DIAMOND_PICKAXE;
            case GOBLIN -> Material.NETHERITE_PICKAXE;
            default -> Material.IRON_PICKAXE;
        };
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(tier.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(tier.color + "Инструмент шахтёра уровня " + tier.name());
        if (tier == PickTier.GOBLIN) {
            lore.add("§7Легендарная кирка глубинных мастеров");
        }
        if (tier != PickTier.VANILLA) {
            lore.add("§8Неломаемая");
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(pickTierKey, PersistentDataType.STRING, tier.name());
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /* ================= Фрагменты ================= */

    public ItemStack createFragment(PickTier tier, int amount) {
        if (tier == PickTier.VANILLA) return null;
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(tier.fragmentDisplayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Собери §e3 §7фрагмента для крафта:");
        lore.add("§7" + tier.displayName);
        lore.add("§8Шанс успеха крафта: 25%");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(pickFragmentKey, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    public PickTier fragmentTierOf(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String tag = meta.getPersistentDataContainer().get(pickFragmentKey, PersistentDataType.STRING);
        if (tag == null) return null;
        try {
            return PickTier.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ================= Золотой самородок ================= */

    public ItemStack createGoldenNugget(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName("§6✦ Золотой самородок ✦");
        List<String> lore = new ArrayList<>();
        lore.add("§eРедчайшая находка шахтёра");
        lore.add("§7Материал легендарных крафтов");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(goldenNuggetKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGoldenNugget(ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(goldenNuggetKey, PersistentDataType.BYTE);
    }
}

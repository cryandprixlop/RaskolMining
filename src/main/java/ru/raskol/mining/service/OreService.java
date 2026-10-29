package ru.raskol.mining.service;

import org.bukkit.Material;
import ru.raskol.mining.RaskolMining;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Кеш списков руд и флагов защиты из конфига.
 * Перечитывается через /mine reload (без рестарта).
 */
public final class OreService {

    private final RaskolMining plugin;
    private Set<Material> ores = EnumSet.noneOf(Material.class);
    private Set<Material> goldOres = EnumSet.noneOf(Material.class);
    private Set<Material> extraBreakable = EnumSet.noneOf(Material.class);
    private boolean wildernessProtect;
    private boolean oresOverrideTowny;

    public OreService(RaskolMining plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ores = readMaterials("ores");
        if (ores.isEmpty()) {
            plugin.getLogger().warning("[Mining] список 'ores' пуст — добыча руды отключена.");
        }
        Set<Material> gold = readMaterials("gold-ores");
        gold.retainAll(ores); // gold-ores должен входить в ores; лишнее вырезается
        goldOres = gold;
        extraBreakable = readMaterials("wilderness-protect.allowed-extra");
        wildernessProtect = plugin.getConfig().getBoolean("wilderness-protect.enabled", true);
        oresOverrideTowny = plugin.getConfig().getBoolean("ores-override-towny", true);
    }

    private Set<Material> readMaterials(String path) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        List<String> list = plugin.getConfig().getStringList(path);
        for (String name : list) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                plugin.getLogger().warning("[Mining] неизвестный материал в конфиге (" + path + "): " + name);
            } else {
                set.add(m);
            }
        }
        return set;
    }

    public boolean isOre(Material m) { return ores.contains(m); }
    public boolean isGoldOre(Material m) { return goldOres.contains(m); }
    public boolean isExtraBreakable(Material m) { return extraBreakable.contains(m); }
    public boolean wildernessProtect() { return wildernessProtect; }
    public boolean oresOverrideTowny() { return oresOverrideTowny; }
}

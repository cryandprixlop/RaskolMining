package ru.raskol.mining.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.License;
import ru.raskol.mining.model.LicenseType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Лицензии по типам + цены наций: mining-licenses.yml.
 * Формат обратно совместим со старым (licenses + town-prices).
 * Защита данных: при ошибке чтения выставляется loadFailed — save() не перезаписывает файл.
 * Запись атомарная: во временный .tmp, затем Files.move(REPLACE_EXISTING).
 */
public final class LicenseStore {

    private final RaskolMining plugin;
    private final Map<LicenseType, Map<String, License>> licenses = new EnumMap<>(LicenseType.class);
    private final Map<LicenseType, Map<String, Double>> nationPrices = new EnumMap<>(LicenseType.class);
    private final Map<String, Double> legacyTownPrices = new HashMap<>();
    private volatile boolean loadFailed = false;

    public LicenseStore(RaskolMining plugin) {
        this.plugin = plugin;
        for (LicenseType t : LicenseType.values()) {
            licenses.put(t, new HashMap<>());
            nationPrices.put(t, new HashMap<>());
        }
    }

    private File file() { return new File(plugin.getDataFolder(), "mining-licenses.yml"); }

    public boolean isLoadFailed() { return loadFailed; }

    public void load() {
        for (LicenseType t : LicenseType.values()) {
            licenses.get(t).clear();
            nationPrices.get(t).clear();
        }
        legacyTownPrices.clear();
        loadFailed = false;

        File file = file();
        if (!file.exists()) return;

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (Exception e) {
            loadFailed = true;
            plugin.getLogger().severe("[Mining] mining-licenses.yml не читается (" + e.getMessage()
                    + "). Файл НЕ будет перезаписан, покупки не затрут данные.");
            return;
        }

        loadSection(cfg.getConfigurationSection("licenses"), LicenseType.MINER);
        loadSection(cfg.getConfigurationSection("gold-licenses"), LicenseType.GOLD);

        ConfigurationSection np = cfg.getConfigurationSection("nation-prices");
        if (np != null) {
            for (String typeKey : np.getKeys(false)) {
                LicenseType t = LicenseType.byId(typeKey);
                if (t == null) continue;
                ConfigurationSection s = np.getConfigurationSection(typeKey);
                if (s == null) continue;
                for (String nationId : s.getKeys(false)) {
                    nationPrices.get(t).put(nationId, s.getDouble(nationId, 0.0));
                }
            }
        }

        // устаревшее: цены по названию города, только чтение, только MINER
        ConfigurationSection tp = cfg.getConfigurationSection("town-prices");
        if (tp != null) {
            for (String town : tp.getKeys(false)) {
                legacyTownPrices.put(town, tp.getDouble(town, 0.0));
            }
        }
    }

    private void loadSection(ConfigurationSection s, LicenseType type) {
        if (s == null) return;
        for (String uuid : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(uuid);
            if (e == null) continue;
            License lic = new License();
            lic.uuid = uuid;
            lic.name = e.getString("name", "");
            lic.expiresAtEpochSeconds = e.getLong("expires", 0);
            if (lic.isValid()) licenses.get(type).put(uuid, lic);
        }
    }

    public void save() {
        if (loadFailed) {
            plugin.getLogger().severe("[Mining] save пропущен: mining-licenses.yml не читался при загрузке.");
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (LicenseType t : LicenseType.values()) {
            String section = (t == LicenseType.MINER) ? "licenses" : "gold-licenses";
            for (License lic : licenses.get(t).values()) {
                if (!lic.isValid()) continue;
                ConfigurationSection s = cfg.createSection(section + "." + lic.uuid);
                s.set("name", lic.name);
                s.set("expires", lic.expiresAtEpochSeconds);
            }
            for (Map.Entry<String, Double> e : nationPrices.get(t).entrySet()) {
                cfg.set("nation-prices." + t.id + "." + e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Double> e : legacyTownPrices.entrySet()) {
            cfg.set("town-prices." + e.getKey(), e.getValue());
        }

        File file = file();
        File tmp = new File(plugin.getDataFolder(), "mining-licenses.yml.tmp");
        try {
            cfg.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().severe("[Mining] не удалось сохранить mining-licenses.yml: " + e.getMessage());
        }
    }

    /* ================= доступ ================= */

    public License get(LicenseType type, String uuid) {
        return licenses.get(type).get(uuid);
    }

    public int activeCount(LicenseType type) {
        int count = 0;
        for (License lic : licenses.get(type).values()) {
            if (lic.isValid()) count++;
        }
        return count;
    }

    /** Прибавляет срок к остатку (для extendable); истёкшие начинают с now. */
    public void issue(LicenseType type, String uuid, String name, int days) {
        long now = System.currentTimeMillis() / 1000L;
        License lic = licenses.get(type).get(uuid);
        if (lic == null) {
            lic = new License();
            lic.uuid = uuid;
            licenses.get(type).put(uuid, lic);
        }
        lic.name = name;
        lic.expiresAtEpochSeconds = Math.max(now, lic.expiresAtEpochSeconds) + (long) days * 86400L;
        save();
    }

    /* ================= цены ================= */

    public double getNationPrice(LicenseType type, String nationId, double def) {
        return nationPrices.get(type).getOrDefault(nationId, def);
    }

    public void setNationPrice(LicenseType type, String nationId, double price) {
        nationPrices.get(type).put(nationId, price);
        save();
    }

    /** Устаревшая цена по имени города (только MINER), null если нет. */
    public Double legacyTownPrice(String town) {
        return legacyTownPrices.get(town);
    }
}

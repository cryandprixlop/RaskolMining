package ru.raskol.mining.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.License;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class LicenseStore {

    private final RaskolMining plugin;
    private final Map<String, License> licenses = new HashMap<>();
    private final Map<String, Double> townPrices = new HashMap<>();

    public LicenseStore(RaskolMining plugin) { this.plugin = plugin; }

    public void load() {
        licenses.clear();
        townPrices.clear();
        File file = new File(plugin.getDataFolder(), "mining-licenses.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection ls = cfg.getConfigurationSection("licenses");
        if (ls != null) {
            for (String uuid : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(uuid);
                if (s == null) continue;
                License lic = new License();
                lic.uuid = uuid;
                lic.name = s.getString("name", "");
                lic.expiresAtEpochSeconds = s.getLong("expires", 0);
                if (lic.isValid()) licenses.put(uuid, lic);
            }
        }
        ConfigurationSection tp = cfg.getConfigurationSection("town-prices");
        if (tp != null) {
            for (String town : tp.getKeys(false)) {
                townPrices.put(town, tp.getDouble(town, 500.0));
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (License lic : licenses.values()) {
            if (!lic.isValid()) continue;
            ConfigurationSection s = cfg.createSection("licenses." + lic.uuid);
            s.set("name", lic.name);
            s.set("expires", lic.expiresAtEpochSeconds);
        }
        for (Map.Entry<String, Double> e : townPrices.entrySet()) {
            cfg.set("town-prices." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), "mining-licenses.yml"));
        } catch (Exception e) {
            plugin.getLogger().severe("[Mining] не удалось сохранить mining-licenses.yml: " + e.getMessage());
        }
    }

    public License get(String uuid) { return licenses.get(uuid); }

    public int activeCount() {
        int count = 0;
        for (License lic : licenses.values()) {
            if (lic.isValid()) count++;
        }
        return count;
    }

    public void issue(String uuid, String name, int days) {
        long now = System.currentTimeMillis() / 1000L;
        License lic = licenses.get(uuid);
        if (lic == null) {
            lic = new License();
            lic.uuid = uuid;
            lic.name = name;
            lic.expiresAtEpochSeconds = now + (long) days * 86400L;
        } else {
            long base = Math.max(now, lic.expiresAtEpochSeconds);
            lic.expiresAtEpochSeconds = base + (long) days * 86400L;
        }
        licenses.put(uuid, lic);
        save();
    }

    public double townPrice(String town, double def) {
        return townPrices.getOrDefault(town, def);
    }

    public void setTownPrice(String town, double price) {
        townPrices.put(town, price);
        save();
    }
}

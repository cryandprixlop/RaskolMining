package ru.raskol.mining.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PendingOre;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MiningStore {

    private final RaskolMining plugin;
    private final List<PendingOre> pending = new ArrayList<>();

    public MiningStore(RaskolMining plugin) { this.plugin = plugin; }

    public void load() {
        pending.clear();
        File file = new File(plugin.getDataFolder(), "mining.yml");
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = cfg.getConfigurationSection("pending");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            ConfigurationSection s = ps.getConfigurationSection(key);
            if (s == null) continue;
            PendingOre p = new PendingOre();
            p.world = s.getString("world", "world");
            p.x = s.getInt("x");
            p.y = s.getInt("y");
            p.z = s.getInt("z");
            p.material = s.getString("material", "STONE");
            p.restoreAtEpochSeconds = s.getLong("restore-at", 0);
            pending.add(p);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < pending.size(); i++) {
            PendingOre p = pending.get(i);
            ConfigurationSection s = cfg.createSection("pending." + i);
            s.set("world", p.world);
            s.set("x", p.x);
            s.set("y", p.y);
            s.set("z", p.z);
            s.set("material", p.material);
            s.set("restore-at", p.restoreAtEpochSeconds);
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), "mining.yml"));
        } catch (Exception e) {
            plugin.getLogger().severe("[Mining] не удалось сохранить mining.yml: " + e.getMessage());
        }
    }

    public void addPending(PendingOre p) { pending.add(p); save(); }
    public List<PendingOre> pending() { return pending; }
}

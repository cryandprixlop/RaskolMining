package ru.raskol.mining.task;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PendingOre;
import ru.raskol.mining.store.MiningStore;

import java.util.ArrayList;
import java.util.List;

/** Раз в минуту проверяет очередь и возвращает руду на место. */
public final class OreRegenTask implements Runnable {

    private final RaskolMining plugin;
    private final MiningStore store;

    public OreRegenTask(RaskolMining plugin, MiningStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis() / 1000L;
        List<PendingOre> done = new ArrayList<>();
        int restored = 0;

        for (PendingOre p : store.pending()) {
            if (p.restoreAtEpochSeconds > now) continue;

            World w = Bukkit.getWorld(p.world);
            if (w == null) continue; // мир не загружен — попробуем в следующий раз

            Block b = w.getBlockAt(p.x, p.y, p.z);
            Material current = b.getType();
            if (current == Material.AIR || current == Material.CAVE_AIR || current == Material.VOID_AIR) {
                Material ore = Material.matchMaterial(p.material);
                if (ore != null) {
                    b.setType(ore, false);
                    restored++;
                }
                done.add(p);
            }
            // место занято построкой — оставляем в очереди, ретрай в следующем цикле
        }

        if (!done.isEmpty()) {
            store.pending().removeAll(done);
            store.save();
            if (restored > 0) {
                plugin.getLogger().info("[Mining] регенерация: восстановлено руды: " + restored
                        + ", в очереди осталось: " + store.pending().size());
            }
        }
    }
}

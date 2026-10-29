package ru.raskol.mining.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PendingOre;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.service.OreService;
import ru.raskol.mining.service.PickService;
import ru.raskol.mining.store.MiningStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * При добыче руды:
 * 1) блок встаёт в очередь регенерации (interval-hours);
 * 2) рулетка лута кирки: самородок -> фрагмент -> множитель дропа.
 * Шансы берутся из конфига loot.*, при отсутствии ключа — встроенные дефолты.
 */
public final class OreRegenListener implements Listener {

    private static final long ROLL_WINDOW_MS = 2000;

    private static final Map<String, Double> DEFAULTS = Map.ofEntries(
            Map.entry("vanilla.golden-chance", 0.001),
            Map.entry("vanilla.next-fragment-chance", 5.0),
            Map.entry("vanilla.double-chance", 2.0),
            Map.entry("veteran.golden-chance", 0.002),
            Map.entry("veteran.next-fragment-chance", 4.0),
            Map.entry("veteran.double-chance", 4.0),
            Map.entry("dwarf.golden-chance", 0.01),
            Map.entry("dwarf.next-fragment-chance", 4.0),
            Map.entry("dwarf.double-chance", 20.0),
            Map.entry("goblin.golden-chance", 0.5),
            Map.entry("goblin.double-chance", 30.0),
            Map.entry("goblin.triple-chance", 12.0),
            Map.entry("goblin.quad-chance", 8.0)
    );

    private final RaskolMining plugin;
    private final MiningStore store;
    private final PickService pickService;
    private final OreService oreService;
    private final Map<UUID, Long> lastRoll = new HashMap<>();

    public OreRegenListener(RaskolMining plugin, MiningStore store,
                            PickService pickService, OreService oreService) {
        this.plugin = plugin;
        this.store = store;
        this.pickService = pickService;
        this.oreService = oreService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!oreService.isOre(block.getType())) return;

        Player player = event.getPlayer();

        // 1) Очередь регенерации
        double hours = plugin.getConfig().getDouble("regen.interval-hours", 3.0);
        PendingOre p = new PendingOre();
        p.world = block.getWorld().getName();
        p.x = block.getX();
        p.y = block.getY();
        p.z = block.getZ();
        p.material = block.getType().name();
        p.restoreAtEpochSeconds = System.currentTimeMillis() / 1000L + (long) (hours * 3600.0);
        store.addPending(p);
        plugin.getLogger().info("[Mining] queued regen: " + p.material + " at "
                + p.x + "," + p.y + "," + p.z + " in " + (int) (hours * 60) + " min");

        // 2) Рулетка лута (одна на игрока в окне 2 сек)
        ItemStack tool = player.getInventory().getItemInMainHand();
        PickTier tier = pickService.tierOf(tool);
        if (tier == null) return;

        long now = System.currentTimeMillis();
        Long prev = lastRoll.get(player.getUniqueId());
        if (prev != null && now - prev < ROLL_WINDOW_MS) return;
        lastRoll.put(player.getUniqueId(), now);

        rollLoot(player, tier, block, tool);
    }

    private void rollLoot(Player player, PickTier tier, Block block, ItemStack tool) {
        String section = sectionOf(tier);
        double roll = Math.random() * 100.0;
        double acc = 0.0;

        double golden = chance(section, "golden-chance");
        acc += golden;
        if (roll < acc) {
            give(player, pickService.createGoldenNugget(1));
            player.sendMessage("§6✦ Редчайшая находка: §eЗолотой самородок§6!");
            plugin.getLogger().info("[Mining] " + player.getName()
                    + " нашёл золотой самородок (" + tier.name() + ")");
            return;
        }

        double fragChance = chance(section, "next-fragment-chance");
        acc += fragChance;
        if (roll < acc) {
            PickTier fragTier = tier.dropsFragment();
            if (fragTier != null) {
                give(player, pickService.createFragment(fragTier, 1));
                player.sendMessage("§7⚙ Найден " + fragTier.fragmentDisplayName + "§7!");
                plugin.getLogger().info("[Mining] " + player.getName()
                        + " нашёл фрагмент " + fragTier.name());
            }
            return;
        }

        double quad = chance(section, "quad-chance");
        double triple = chance(section, "triple-chance");
        double dbl = chance(section, "double-chance");
        double mRoll = Math.random() * 100.0;
        int multiplier = 1;
        if (mRoll < quad) multiplier = 4;
        else if (mRoll < quad + triple) multiplier = 3;
        else if (mRoll < quad + triple + dbl) multiplier = 2;

        if (multiplier > 1) {
            for (ItemStack drop : block.getDrops(tool)) {
                ItemStack extra = drop.clone();
                extra.setAmount(drop.getAmount() * (multiplier - 1));
                block.getWorld().dropItemNaturally(block.getLocation(), extra);
            }
            player.sendMessage("§e✦ Богатая жила! §7×" + multiplier + " дропа");
            plugin.getLogger().info("[Mining] " + player.getName()
                    + ": множитель ×" + multiplier + " (" + tier.name() + ")");
        }
    }

    private double chance(String section, String key) {
        return plugin.getConfig().getDouble("loot." + section + "." + key,
                DEFAULTS.getOrDefault(section + "." + key, 0.0));
    }

    private String sectionOf(PickTier tier) {
        return switch (tier) {
            case VANILLA -> "vanilla";
            case VETERAN -> "veteran";
            case DWARF -> "dwarf";
            case GOBLIN -> "goblin";
        };
    }

    private void give(Player player, ItemStack item) {
        if (item == null) return;
        for (ItemStack drop : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), drop);
        }
    }
}

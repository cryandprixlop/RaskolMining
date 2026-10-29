package ru.raskol.mining.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.hook.TownyHook;
import ru.raskol.mining.model.LicenseType;
import ru.raskol.mining.service.LicenseService;
import ru.raskol.mining.service.OreService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Гейты лицензий и защиты диких земель.
 * onOreBreak (LOWEST, без ignoreCancelled): лицензии на руду; при действующей лицензии
 * и флаге ores-override-towny снимает отмену, поставленную раньше нас (Towny).
 * onWildernessBreak (HIGH, ignoreCancelled): в диких землях ломаются только руда и allowed-extra.
 */
public final class MiningLicenseListener implements Listener {

    private final RaskolMining plugin;
    private final LicenseService licenseService;
    private final OreService oreService;
    private final TownyHook towny;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public MiningLicenseListener(RaskolMining plugin, LicenseService licenseService,
                                 OreService oreService, TownyHook towny) {
        this.plugin = plugin;
        this.licenseService = licenseService;
        this.oreService = oreService;
        this.towny = towny;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOreBreak(BlockBreakEvent event) {
        Material mat = event.getBlock().getType();
        if (!oreService.isOre(mat)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("mining.admin")) return;

        LicenseType type = oreService.isGoldOre(mat) ? LicenseType.GOLD : LicenseType.MINER;
        if (!licenseService.hasValidLicense(player, type)) {
            event.setCancelled(true);
            warn(player, type == LicenseType.GOLD
                    ? "§cДля добычи золота нужна лицензия на золото! §e/mine license buy gold"
                    : "§cДля добычи руды нужна лицензия шахтёра! §e/mine license buy");
            return;
        }

        // Лицензия есть: руда приоритетнее защиты Towny (флаг конфига)
        if (event.isCancelled() && oreService.oresOverrideTowny()) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWildernessBreak(BlockBreakEvent event) {
        if (!oreService.wildernessProtect()) return;
        if (!towny.isAvailable()) return;

        Block block = event.getBlock();
        Material mat = block.getType();
        if (oreService.isOre(mat) || oreService.isExtraBreakable(mat)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("mining.admin")) return;

        Boolean wild = towny.isWilderness(block);
        if (wild == null) return;   // определить не удалось — решает Towny
        if (!wild) return;          // город — действуют правила Towny

        event.setCancelled(true);
        warn(player, "§cВ диких землях можно добывать только руду!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarn.remove(event.getPlayer().getUniqueId());
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long prev = lastWarn.get(player.getUniqueId());
        if (prev != null && now - prev < 3000) return;
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}

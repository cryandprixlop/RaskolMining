package ru.raskol.mining.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.service.LicenseService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Без лицензии шахтёра руда не добывается (HIGH — раньше регена и лута). */
public final class MiningLicenseListener implements Listener {

    private final RaskolMining plugin;
    private final LicenseService licenseService;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public MiningLicenseListener(RaskolMining plugin, LicenseService licenseService) {
        this.plugin = plugin;
        this.licenseService = licenseService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        String mat = event.getBlock().getType().name();
        if (!plugin.getConfig().getStringList("ores").contains(mat)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("mining.admin")) return;
        if (licenseService.hasValidLicense(player)) return;

        event.setCancelled(true);
        warn(player, "§cДля добычи руды нужна лицензия шахтёра! §e/mine license buy");
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long prev = lastWarn.get(player.getUniqueId());
        if (prev != null && now - prev < 3000) return;
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}

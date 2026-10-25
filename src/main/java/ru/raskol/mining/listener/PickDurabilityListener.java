package ru.raskol.mining.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.service.PickService;

/** Кастомные кирки неломаемые: отменяем поломку по PDC-тегу (и для старых выдач). */
public final class PickDurabilityListener implements Listener {

    private final RaskolMining plugin;
    private final PickService pickService;

    public PickDurabilityListener(RaskolMining plugin, PickService pickService) {
        this.plugin = plugin;
        this.pickService = pickService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (pickService.isCustomPick(event.getItem())) {
            event.setCancelled(true);
        }
    }
}

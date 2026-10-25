package ru.raskol.mining.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.service.PickService;

import java.util.Random;

/** Крафт кирок: 3 фрагмента нужного тира, шанс успеха 25%, провал = фрагменты сгорели. */
public final class PickCraftListener implements Listener {

    private final RaskolMining plugin;
    private final PickService pickService;
    private final Random random = new Random();

    public PickCraftListener(RaskolMining plugin, PickService pickService) {
        this.plugin = plugin;
        this.pickService = pickService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapedRecipe sr)) return;
        if (!isOurRecipe(sr)) return;

        PickTier target = targetTier(sr);
        if (target == null || !validateMatrix(event.getInventory(), target)) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(pickService.createPick(target));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof ShapedRecipe sr)) return;
        if (!isOurRecipe(sr)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        PickTier target = targetTier(sr);
        if (target == null || !validateMatrix(event.getInventory(), target)) return;

        event.setCancelled(true);
        consumeFragments(event.getInventory());

        double chance = plugin.getConfig().getDouble("pick.craft-success-chance", 25.0);
        if (random.nextDouble(100.0) < chance) {
            give(player, pickService.createPick(target));
            player.sendMessage("§a✔ Ковка удалась! Получена: " + target.displayName);
            plugin.getLogger().info("[Mining] craft OK: " + player.getName() + " -> " + target.name());
        } else {
            player.sendMessage("§c✖ Ковка провалилась: фрагменты сгорели в горне!");
            plugin.getLogger().info("[Mining] craft FAIL: " + player.getName() + " -> " + target.name());
        }
    }

    private boolean isOurRecipe(ShapedRecipe sr) {
        return sr.getKey().getNamespace().equals(plugin.getName().toLowerCase())
                && sr.getKey().getKey().startsWith("pick_craft_");
    }

    private PickTier targetTier(ShapedRecipe sr) {
        String key = sr.getKey().getKey();
        if (!key.startsWith("pick_craft_")) return null;
        try {
            return PickTier.valueOf(key.substring("pick_craft_".length()).toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean validateMatrix(CraftingInventory inv, PickTier target) {
        PickTier fragTier = target.craftFragment();
        if (fragTier == null) return false;
        int count = 0;
        for (ItemStack s : inv.getMatrix()) {
            if (s == null || s.getType() == Material.AIR) continue;
            if (pickService.fragmentTierOf(s) != fragTier) return false;
            count++;
        }
        return count >= 3;
    }

    private void consumeFragments(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        int remaining = 3;
        for (int i = 0; i < matrix.length && remaining > 0; i++) {
            ItemStack s = matrix[i];
            if (s == null || s.getType() == Material.AIR) continue;
            if (pickService.fragmentTierOf(s) == null) continue;
            int take = Math.min(s.getAmount(), remaining);
            if (take >= s.getAmount()) matrix[i] = null;
            else s.setAmount(s.getAmount() - take);
            remaining -= take;
        }
        inv.setMatrix(matrix);
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack drop : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), drop);
        }
    }
}

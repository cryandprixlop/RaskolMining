package ru.raskol.mining;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.mining.command.MineCommand;
import ru.raskol.mining.listener.OreRegenListener;
import ru.raskol.mining.listener.PickCraftListener;
import ru.raskol.mining.listener.PickDurabilityListener;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.service.PickService;
import ru.raskol.mining.store.MiningStore;
import ru.raskol.mining.task.OreRegenTask;

public final class RaskolMining extends JavaPlugin {

    private MiningStore store;
    private PickService pickService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new MiningStore(this);
        store.load();
        pickService = new PickService(this);

        registerPickRecipes();

        PluginCommand cmd = getCommand("mine");
        if (cmd != null) {
            MineCommand executor = new MineCommand(this, store, pickService);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new OreRegenListener(this, store, pickService), this);
        getServer().getPluginManager().registerEvents(
                new PickCraftListener(this, pickService), this);
        getServer().getPluginManager().registerEvents(
                new PickDurabilityListener(this, pickService), this);

        // Проверка очереди регенерации раз в минуту
        getServer().getScheduler().runTaskTimer(this,
                new OreRegenTask(this, store), 200L, 20L * 60);

        getLogger().info("RaskolMining v" + getDescription().getVersion()
                + " включён. В очереди регенерации: " + store.pending().size()
                + " блоков. Интервал: " + getConfig().getDouble("regen.interval-hours", 3.0) + " ч.");
    }

    /** 3 фрагмента в ряд -> кирка тира (исход рулетки решает PickCraftListener). */
    private void registerPickRecipes() {
        int count = 0;
        for (PickTier tier : new PickTier[]{PickTier.VETERAN, PickTier.DWARF, PickTier.GOBLIN}) {
            NamespacedKey key = new NamespacedKey(this, "pick_craft_" + tier.name().toLowerCase());
            if (getServer().getRecipe(key) != null) continue;
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.IRON_PICKAXE));
            recipe.shape("   ", "FFF", "   ");
            recipe.setIngredient('F', Material.AMETHYST_SHARD);
            getServer().addRecipe(recipe);
            count++;
        }
        getLogger().info("[Mining] рецептов кирок зарегистрировано: " + count);
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        getLogger().info("RaskolMining выключен.");
    }

    public MiningStore getStore() { return store; }
    public PickService getPickService() { return pickService; }
}

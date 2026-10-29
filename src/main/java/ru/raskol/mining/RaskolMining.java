package ru.raskol.mining;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.mining.command.MineCommand;
import ru.raskol.mining.hook.TownyHook;
import ru.raskol.mining.hook.VaultHook;
import ru.raskol.mining.listener.MiningLicenseListener;
import ru.raskol.mining.listener.OreRegenListener;
import ru.raskol.mining.listener.PickCraftListener;
import ru.raskol.mining.listener.PickDurabilityListener;
import ru.raskol.mining.model.LicenseType;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.service.LicenseService;
import ru.raskol.mining.service.OreService;
import ru.raskol.mining.service.PickService;
import ru.raskol.mining.store.LicenseStore;
import ru.raskol.mining.store.MiningStore;
import ru.raskol.mining.task.OreRegenTask;

public final class RaskolMining extends JavaPlugin {

    private MiningStore store;
    private LicenseStore licenseStore;
    private TownyHook townyHook;
    private VaultHook vaultHook;
    private PickService pickService;
    private LicenseService licenseService;
    private OreService oreService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new MiningStore(this);
        store.load();
        licenseStore = new LicenseStore(this);
        licenseStore.load();
        townyHook = new TownyHook(this);
        vaultHook = new VaultHook(this);
        pickService = new PickService(this);
        oreService = new OreService(this);
        licenseService = new LicenseService(this, licenseStore, vaultHook, townyHook);

        registerPickRecipes();

        PluginCommand cmd = getCommand("mine");
        if (cmd != null) {
            MineCommand executor = new MineCommand(this, store, pickService,
                    licenseService, licenseStore, townyHook);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new MiningLicenseListener(this, licenseService, oreService, townyHook), this);
        getServer().getPluginManager().registerEvents(
                new OreRegenListener(this, store, pickService, oreService), this);
        getServer().getPluginManager().registerEvents(
                new PickCraftListener(this, pickService), this);
        getServer().getPluginManager().registerEvents(
                new PickDurabilityListener(this, pickService), this);

        getServer().getScheduler().runTaskTimer(this,
                new OreRegenTask(this, store), 200L, 20L * 60);

        getLogger().info("RaskolMining v" + getPluginMeta().getVersion()
                + " включён. В очереди регенерации: " + store.pending().size()
                + " блоков. Лицензий: шахтёр=" + licenseStore.activeCount(LicenseType.MINER)
                + "/золото=" + licenseStore.activeCount(LicenseType.GOLD)
                + ". Интервал: " + getConfig().getDouble("regen.interval-hours", 3.0) + " ч."
                + " Towny: " + (townyHook.isAvailable() ? "да" : "нет")
                + ". Vault: " + (vaultHook.isAvailable() ? "да" : "нет") + ".");
    }

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
        if (licenseStore != null) licenseStore.save();
        getLogger().info("RaskolMining выключен.");
    }

    public MiningStore getStore() { return store; }
    public PickService getPickService() { return pickService; }
    public LicenseService getLicenseService() { return licenseService; }
    public OreService getOreService() { return oreService; }
}

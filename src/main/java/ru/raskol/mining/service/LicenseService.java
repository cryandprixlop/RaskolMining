package ru.raskol.mining.service;

import org.bukkit.entity.Player;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.hook.TownyHook;
import ru.raskol.mining.hook.VaultHook;
import ru.raskol.mining.model.License;
import ru.raskol.mining.model.LicenseType;
import ru.raskol.mining.store.LicenseStore;

/** Покупка лицензий: miner (продлевается) и gold (без продления заранее). */
public final class LicenseService {

    private final RaskolMining plugin;
    private final LicenseStore store;
    private final VaultHook vault;
    private final TownyHook towny;

    public LicenseService(RaskolMining plugin, LicenseStore store, VaultHook vault, TownyHook towny) {
        this.plugin = plugin;
        this.store = store;
        this.vault = vault;
        this.towny = towny;
    }

    /** Порядок: цена нации -> (только MINER) старая цена города -> default-price из конфига. */
    public double priceFor(Player player, LicenseType type) {
        double def = plugin.getConfig().getDouble(type.configPath + ".default-price", 500.0);
        if (!towny.isAvailable()) return def;

        String nationId = towny.residentNationId(player);
        if (nationId != null) {
            double p = store.getNationPrice(type, nationId, Double.NaN);
            if (!Double.isNaN(p)) return p;
        }
        if (type == LicenseType.MINER) {
            String town = towny.residentTownName(player);
            if (town != null) {
                Double legacy = store.legacyTownPrice(town);
                if (legacy != null) return legacy;
            }
        }
        return def;
    }

    public int daysFor(LicenseType type) {
        return plugin.getConfig().getInt(type.configPath + ".duration-days",
                type == LicenseType.GOLD ? 7 : 30);
    }

    public boolean hasValidLicense(Player player, LicenseType type) {
        License lic = store.get(type, player.getUniqueId().toString());
        return lic != null && lic.isValid();
    }

    public BuyResult buy(Player player, LicenseType type) {
        // 1) только житель города
        String town = towny.residentTownName(player);
        if (town == null) {
            return new BuyResult(false, "§cЛицензия выдаётся только жителям городов. Вступите в город.");
        }
        // 2) запрет продления заранее для неextendable
        if (!type.extendable && hasValidLicense(player, type)) {
            License lic = store.get(type, player.getUniqueId().toString());
            return new BuyResult(false, "§cЛицензия " + type.title + " ещё действует ("
                    + lic.remainingFormatted() + "). Продление заранее недоступно — покупка после окончания.");
        }

        double price = priceFor(player, type);
        int days = daysFor(type);

        if (!vault.isAvailable()) return new BuyResult(false, "§cЭкономика недоступна.");
        if (!vault.has(player, price)) {
            return new BuyResult(false, "§cНедостаточно средств: нужно §b" + vault.format(price)
                    + "§c, у тебя §b" + vault.format(vault.balance(player)) + "§c.");
        }
        if (!vault.withdraw(player, price)) {
            return new BuyResult(false, "§cНе удалось списать деньги.");
        }

        // деньги в банк Towny города, иначе в Vault-аккаунт города
        String via = null;
        if (towny.depositTownBank(town, price, "RaskolMining: лицензия " + type.id + " " + player.getName())) {
            via = "банк Towny города " + town;
        } else {
            String account = plugin.getConfig().getString("town-account-prefix", "town-") + town.toLowerCase();
            vault.createAccount(account);
            if (vault.depositAccount(account, price)) via = "аккаунт " + account;
        }
        if (via == null) {
            if (!vault.deposit(player, price)) {
                plugin.getLogger().severe("[Mining] НЕ удалось вернуть деньги " + player.getName()
                        + " (" + price + ") при сбое казны города " + town + "!");
            }
            return new BuyResult(false, "§cКазна города недоступна — операция отменена, деньги возвращены.");
        }

        store.issue(type, player.getUniqueId().toString(), player.getName(), days);
        return new BuyResult(true, "§a✔ Лицензия " + type.title + " получена на §e" + days
                + " дн.§a. Заплачено §b" + vault.format(price) + "§a → " + via + ".");
    }

    public static final class BuyResult {
        public final boolean success;
        public final String message;
        public BuyResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}

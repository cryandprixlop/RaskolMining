package ru.raskol.mining.hook;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.raskol.mining.RaskolMining;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Towny через рефлексию с КЕШИРОВАНИЕМ Class/Method/экземпляров (п.3.2 ТЗ).
 * Каждая точка сбоя пишет warnOnce — одно предупреждение на метод, без спама.
 */
public final class TownyHook {

    private final RaskolMining plugin;
    private final Map<String, Boolean> warned = new HashMap<>();

    private boolean initialized = false;
    private boolean ok = false;

    private Object universe;   // TownyUniverse instance
    private Object api;        // TownyAPI instance
    private Method mGetResident, mHasTown, mGetTownOrNull, mGetName, mGetNationOrNull,
            mGetUUID, mIsKing, mIsWilderness, mGetTown, mGetAccount, mDeposit;

    public TownyHook(RaskolMining plugin) { this.plugin = plugin; }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Towny") != null;
    }

    /** Однократная инициализация рефлексии; результат кешируется. */
    private boolean reflect() {
        if (initialized) return ok;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Class<?> universeClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Class<?> residentClass = Class.forName("com.palmergames.bukkit.towny.object.Resident");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            Class<?> nationClass = Class.forName("com.palmergames.bukkit.towny.object.Nation");
            Class<?> accountClass = Class.forName("com.palmergames.bukkit.towny.object.economy.Account");

            universe = universeClass.getMethod("getInstance").invoke(null);
            api = apiClass.getMethod("getInstance").invoke(null);

            mGetResident = universeClass.getMethod("getResident", UUID.class);
            mHasTown = residentClass.getMethod("hasTown");
            mGetTownOrNull = residentClass.getMethod("getTownOrNull");
            mIsKing = residentClass.getMethod("isKing");
            mGetNationOrNull = residentClass.getMethod("getNationOrNull");
            mGetName = townClass.getMethod("getName");
            mGetAccount = townClass.getMethod("getAccount");
            mGetUUID = nationClass.getMethod("getUUID");
            mGetTown = apiClass.getMethod("getTown", String.class);
            mIsWilderness = apiClass.getMethod("isWilderness", Block.class);
            mDeposit = accountClass.getMethod("deposit", double.class, String.class);
            ok = true;
        } catch (Throwable t) {
            ok = false;
            warnOnce("init", t);
        }
        return ok;
    }

    private void warnOnce(String where, Throwable t) {
        if (warned.putIfAbsent(where, Boolean.TRUE) == null) {
            plugin.getLogger().warning("[Mining] Towny-хук '" + where + "' недоступен: " + t
                    + " (функция отключена, дальше без спама)");
        }
    }

    private Object resident(Player player) throws Exception {
        return mGetResident.invoke(universe, player.getUniqueId());
    }

    public String residentTownName(Player player) {
        if (!isAvailable() || !reflect()) return null;
        try {
            Object r = resident(player);
            if (r == null) return null;
            Boolean hasTown = (Boolean) mHasTown.invoke(r);
            if (hasTown == null || !hasTown) return null;
            Object town = mGetTownOrNull.invoke(r);
            if (town == null) return null;
            return (String) mGetName.invoke(town);
        } catch (Throwable t) {
            warnOnce("residentTownName", t);
            return null;
        }
    }

    /** UUID нации игрока (строкой) или null. */
    public String residentNationId(Player player) {
        if (!isAvailable() || !reflect()) return null;
        try {
            Object r = resident(player);
            if (r == null) return null;
            Object nation = mGetNationOrNull.invoke(r);
            if (nation == null) return null;
            UUID uuid = (UUID) mGetUUID.invoke(nation);
            return uuid == null ? null : uuid.toString();
        } catch (Throwable t) {
            warnOnce("residentNationId", t);
            return null;
        }
    }

    /** true = игрок лидер нации (король). */
    public boolean isKing(Player player) {
        if (!isAvailable() || !reflect()) return false;
        try {
            Object r = resident(player);
            if (r == null) return false;
            Boolean k = (Boolean) mIsKing.invoke(r);
            return k != null && k;
        } catch (Throwable t) {
            warnOnce("isKing", t);
            return false;
        }
    }

    /** true = дикие земли, false = город, null = определить не удалось (решает Towny). */
    public Boolean isWilderness(Block block) {
        if (!isAvailable() || !reflect()) return null;
        try {
            return (Boolean) mIsWilderness.invoke(api, block);
        } catch (Throwable t) {
            warnOnce("isWilderness", t);
            return null;
        }
    }

    public boolean depositTownBank(String townName, double amount, String reason) {
        if (!isAvailable() || !reflect()) return false;
        try {
            Object town = mGetTown.invoke(api, townName);
            if (town == null) return false;
            Object account = mGetAccount.invoke(town);
            if (account == null) return false;
            Object out = mDeposit.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) {
            warnOnce("depositTownBank", t);
            return false;
        }
    }
}

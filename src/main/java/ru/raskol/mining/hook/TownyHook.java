package ru.raskol.mining.hook;

import org.bukkit.entity.Player;
import ru.raskol.mining.RaskolMining;

import java.lang.reflect.Method;

public final class TownyHook {

    private final RaskolMining plugin;

    public TownyHook(RaskolMining plugin) { this.plugin = plugin; }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Towny") != null;
    }

    public String residentTownName(Player player) {
        try {
            Class<?> utilClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Object universe = utilClass.getMethod("getInstance").invoke(null);
            Object resident = universe.getClass().getMethod("getResident", java.util.UUID.class)
                    .invoke(universe, player.getUniqueId());
            if (resident == null) return null;
            Boolean hasTown = (Boolean) resident.getClass().getMethod("hasTown").invoke(resident);
            if (hasTown == null || !hasTown) return null;
            Object town = resident.getClass().getMethod("getTownOrNull").invoke(resident);
            if (town == null) return null;
            return (String) town.getClass().getMethod("getName").invoke(town);
        } catch (Throwable t) { return null; }
    }

    public boolean isKing(Player player) {
        try {
            Class<?> utilClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Object universe = utilClass.getMethod("getInstance").invoke(null);
            Object resident = universe.getClass().getMethod("getResident", java.util.UUID.class)
                    .invoke(universe, player.getUniqueId());
            if (resident == null) return false;
            Boolean isKing = (Boolean) resident.getClass().getMethod("isKing").invoke(resident);
            return isKing != null && isKing;
        } catch (Throwable t) { return false; }
    }

    public boolean depositTownBank(String townName, double amount, String reason) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object town = apiClass.getMethod("getTown", String.class).invoke(api, townName);
            if (town == null) return false;
            Object account = town.getClass().getMethod("getAccount").invoke(town);
            if (account == null) return false;
            Method m = account.getClass().getMethod("deposit", double.class, String.class);
            Object out = m.invoke(account, amount, reason);
            return !(out instanceof Boolean b) || b;
        } catch (Throwable t) { return false; }
    }
}

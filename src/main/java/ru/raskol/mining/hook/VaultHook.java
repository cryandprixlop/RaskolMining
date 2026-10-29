package ru.raskol.mining.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.raskol.mining.RaskolMining;

/**
 * Vault лениво: провайдер ищется при первом обращении и доуспевает,
 * если Vault включился позже нас. Без Vault — нет NoClassDefFoundError
 * (ловим Throwable), все методы безопасно возвращают false/0.
 */
public final class VaultHook {

    private final RaskolMining plugin;
    private Economy economy;

    public VaultHook(RaskolMining plugin) { this.plugin = plugin; }

    private Economy eco() {
        if (economy != null) return economy;
        try {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                    .getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        } catch (Throwable t) {
            economy = null;
        }
        return economy;
    }

    public boolean isAvailable() { return eco() != null; }

    public double balance(Player p) {
        Economy e = eco();
        return e != null ? e.getBalance(p) : 0.0;
    }

    public String format(double a) {
        Economy e = eco();
        return e != null ? e.format(a) : String.valueOf(a);
    }

    public boolean has(Player p, double a) {
        Economy e = eco();
        return e != null && e.has(p, a);
    }

    public boolean withdraw(Player p, double a) {
        Economy e = eco();
        return e != null && e.withdrawPlayer(p, a).transactionSuccess();
    }

    public boolean deposit(Player p, double a) {
        Economy e = eco();
        return e != null && e.depositPlayer(p, a).transactionSuccess();
    }

    public boolean createAccount(String name) {
        Economy e = eco();
        if (e == null) return false;
        try { return e.hasAccount(name) || e.createPlayerAccount(name); }
        catch (Throwable t) { return false; }
    }

    public boolean depositAccount(String name, double amount) {
        Economy e = eco();
        return e != null && e.depositPlayer(name, amount).transactionSuccess();
    }

    public boolean withdrawAccount(String name, double amount) {
        Economy e = eco();
        return e != null && e.withdrawPlayer(name, amount).transactionSuccess();
    }
}

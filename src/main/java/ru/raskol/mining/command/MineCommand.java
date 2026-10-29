package ru.raskol.mining.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.hook.TownyHook;
import ru.raskol.mining.model.License;
import ru.raskol.mining.model.LicenseType;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.model.PendingOre;
import ru.raskol.mining.service.LicenseService;
import ru.raskol.mining.service.PickService;
import ru.raskol.mining.store.LicenseStore;
import ru.raskol.mining.store.MiningStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MineCommand implements CommandExecutor, TabCompleter {

    private final RaskolMining plugin;
    private final MiningStore store;
    private final PickService pickService;
    private final LicenseService licenseService;
    private final LicenseStore licenseStore;
    private final TownyHook towny;

    public MineCommand(RaskolMining plugin, MiningStore store, PickService pickService,
                       LicenseService licenseService, LicenseStore licenseStore, TownyHook towny) {
        this.plugin = plugin;
        this.store = store;
        this.pickService = pickService;
        this.licenseService = licenseService;
        this.licenseStore = licenseStore;
        this.towny = towny;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "license"  -> handleLicense(sender, args);
            case "pick"     -> handlePick(sender, args);
            case "fragment" -> handleFragment(sender, args);
            case "golden"   -> handleGolden(sender, args);
            case "list"     -> handleList(sender);
            case "reload"   -> handleReload(sender);
            default         -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== RaskolMining ===");
        sender.sendMessage("§e/mine license buy [gold] §7— купить лицензию (gold = на золото, 7 дней)");
        sender.sendMessage("§e/mine license status §7— статус обеих лицензий");
        sender.sendMessage("§e/mine license setprice [gold] <цена> §7— (король) цена для своей нации");
        sender.sendMessage("§e/mine pick give <tier> <игрок> [кол-во] §7— выдать кирку");
        sender.sendMessage("§e/mine fragment give <tier> <игрок> [кол-во] §7— выдать фрагмент");
        sender.sendMessage("§e/mine golden give <игрок> [кол-во] §7— выдать золотой самородок");
        sender.sendMessage("§e/mine list §7— очередь регенерации руды");
        sender.sendMessage("§e/mine reload §7— перезагрузить конфиг и кеш руд");
        sender.sendMessage("§7Тиры: VANILLA, VETERAN, DWARF, GOBLIN");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("mining.admin")) return true;
        sender.sendMessage("§cНет прав.");
        return false;
    }

    /* ================= license ================= */

    private void handleLicense(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c/mine license <buy [gold]|status|setprice [gold] <цена>>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "buy" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cТолько игроки."); return; }
                LicenseType type = LicenseType.MINER;
                if (args.length >= 3) {
                    if (args[2].equalsIgnoreCase("gold")) type = LicenseType.GOLD;
                    else { p.sendMessage("§cФормат: /mine license buy [gold]"); return; }
                }
                p.sendMessage(licenseService.buy(p, type).message);
            }
            case "status" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cТолько игроки."); return; }
                String uuid = p.getUniqueId().toString();
                sender.sendMessage("§6=== Лицензии шахтёра ===");
                for (LicenseType t : LicenseType.values()) {
                    License lic = licenseStore.get(t, uuid);
                    if (lic != null && lic.isValid()) {
                        p.sendMessage("§aЛицензия " + t.title + ": активна, осталось §e"
                                + lic.remainingFormatted());
                    } else {
                        p.sendMessage("§7Лицензия " + t.title + ": неактивна. Цена сейчас: §b"
                                + plugin.getVaultHook().format(licenseService.priceFor(p, t))
                                + " §7(/mine license buy" + (t == LicenseType.GOLD ? " gold" : "") + ")");
                    }
                }
            }
            case "setprice" -> handleSetPrice(sender, args);
            default -> sender.sendMessage("§c/mine license <buy [gold]|status|setprice [gold] <цена>>");
        }
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cТолько игроки."); return; }
        LicenseType type = LicenseType.MINER;
        String priceArg;
        if (args.length == 3) {
            priceArg = args[2];
        } else if (args.length == 4 && args[2].equalsIgnoreCase("gold")) {
            type = LicenseType.GOLD;
            priceArg = args[3];
        } else {
            p.sendMessage("§cФормат: /mine license setprice [gold] <цена>");
            return;
        }

        boolean isAdmin = p.hasPermission("mining.admin");
        if (!isAdmin && !towny.isKing(p)) {
            p.sendMessage("§cТолько король может менять цену лицензии.");
            return;
        }
        String nationId = towny.residentNationId(p);
        if (nationId == null) {
            p.sendMessage("§cТы не состоишь в королевстве — цену задавать некому.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceArg);
        } catch (NumberFormatException e) {
            p.sendMessage("§cЦена должна быть числом.");
            return;
        }
        if (Double.isNaN(price) || Double.isInfinite(price)) {
            p.sendMessage("§cЦена должна быть конечным числом.");
            return;
        }
        if (price < 0) {
            p.sendMessage("§cЦена не может быть отрицательной.");
            return;
        }

        licenseStore.setNationPrice(type, nationId, price);
        p.sendMessage("§aЦена лицензии " + type.title + " для твоей нации теперь §b"
                + plugin.getVaultHook().format(price) + "§a.");
        plugin.getLogger().info("[Mining] nation price set: type=" + type.id + " nation=" + nationId
                + " price=" + price + " by " + p.getName());
    }

    /* ================= выдача / список / reload ================= */

    private void handlePick(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§c/mine pick give <VANILLA|VETERAN|DWARF|GOBLIN> <игрок> [кол-во]");
            return;
        }
        PickTier tier;
        try { tier = PickTier.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { sender.sendMessage("§cНеизвестный тир."); return; }
        Player target = plugin.getServer().getPlayer(args[3]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        int amount = parseInt(args.length >= 5 ? args[4] : "1", 1);
        for (int i = 0; i < amount; i++) target.getInventory().addItem(pickService.createPick(tier));
        sender.sendMessage("§aВыдано " + amount + " × " + tier.displayName + " §aигроку " + target.getName());
    }

    private void handleFragment(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§c/mine fragment give <VETERAN|DWARF|GOBLIN> <игрок> [кол-во]");
            return;
        }
        PickTier tier;
        try { tier = PickTier.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { sender.sendMessage("§cНеизвестный тир."); return; }
        if (tier == PickTier.VANILLA) { sender.sendMessage("§cУ обычной кирки нет фрагмента."); return; }
        Player target = plugin.getServer().getPlayer(args[3]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        int amount = parseInt(args.length >= 5 ? args[4] : "1", 1);
        target.getInventory().addItem(pickService.createFragment(tier, amount));
        sender.sendMessage("§aВыдано " + amount + " × " + tier.fragmentDisplayName + " §aигроку " + target.getName());
    }

    private void handleGolden(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§c/mine golden give <игрок> [кол-во]");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[2]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        int amount = parseInt(args.length >= 4 ? args[3] : "1", 1);
        target.getInventory().addItem(pickService.createGoldenNugget(amount));
        sender.sendMessage("§aВыдано " + amount + " × §6✦ Золотой самородок ✦ §aигроку " + target.getName());
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("§6=== Очередь регенерации: " + store.pending().size() + " блоков ===");
        int shown = 0;
        for (PendingOre p : store.pending()) {
            if (shown++ >= 10) {
                sender.sendMessage("§7... и ещё " + (store.pending().size() - 10));
                break;
            }
            long mins = Math.max(0, (p.restoreAtEpochSeconds - System.currentTimeMillis() / 1000L) / 60);
            sender.sendMessage("§e" + p.material + " §7" + p.world + " "
                    + p.x + "," + p.y + "," + p.z + " §7через " + mins + " мин");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadConfig();
        plugin.getOreService().reload();
        sender.sendMessage("§aКонфиг и кеш руд RaskolMining перезагружены.");
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("license", "pick", "fragment", "golden", "list", "reload"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "license" -> filter(Arrays.asList("buy", "status", "setprice"), args[1]);
                case "pick", "fragment", "golden" -> filter(List.of("give"), args[1]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("license")
                    && (args[1].equalsIgnoreCase("buy") || args[1].equalsIgnoreCase("setprice"))) {
                return filter(List.of("gold"), args[2]);
            }
            if (args[0].equalsIgnoreCase("pick") && args[1].equalsIgnoreCase("give")) {
                return filter(Arrays.asList("VANILLA", "VETERAN", "DWARF", "GOBLIN"), args[2]);
            }
            if (args[0].equalsIgnoreCase("fragment") && args[1].equalsIgnoreCase("give")) {
                return filter(Arrays.asList("VETERAN", "DWARF", "GOBLIN"), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}

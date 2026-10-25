package ru.raskol.mining.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.raskol.mining.RaskolMining;
import ru.raskol.mining.model.PickTier;
import ru.raskol.mining.service.PickService;
import ru.raskol.mining.store.MiningStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MineCommand implements CommandExecutor, TabCompleter {

    private final RaskolMining plugin;
    private final MiningStore store;
    private final PickService pickService;

    public MineCommand(RaskolMining plugin, MiningStore store, PickService pickService) {
        this.plugin = plugin;
        this.store = store;
        this.pickService = pickService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
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
        sender.sendMessage("§e/mine pick give <tier> <игрок> [кол-во] §7— выдать кирку");
        sender.sendMessage("§e/mine fragment give <tier> <игрок> [кол-во] §7— выдать фрагмент");
        sender.sendMessage("§e/mine golden give <игрок> [кол-во] §7— выдать золотой самородок");
        sender.sendMessage("§e/mine list §7— очередь регенерации руды");
        sender.sendMessage("§e/mine reload §7— перезагрузить конфиг");
        sender.sendMessage("§7Тиры: VANILLA, VETERAN, DWARF, GOBLIN");
        sender.sendMessage("§7Руда возвращается через "
                + plugin.getConfig().getDouble("regen.interval-hours", 3.0) + " ч.");
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("mining.admin")) return true;
        sender.sendMessage("§cНет прав.");
        return false;
    }

    private void handlePick(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§c/mine pick give <VANILLA|VETERAN|DWARF|GOBLIN> <игрок> [кол-во]");
            return;
        }
        PickTier tier;
        try {
            tier = PickTier.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cНеизвестный тир. Доступны: VANILLA, VETERAN, DWARF, GOBLIN.");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[3]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        int amount = parseInt(args.length >= 5 ? args[4] : "1", 1);
        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(pickService.createPick(tier));
        }
        sender.sendMessage("§aВыдано " + amount + " × " + tier.displayName
                + " §aигроку " + target.getName());
    }

    private void handleFragment(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§c/mine fragment give <VETERAN|DWARF|GOBLIN> <игрок> [кол-во]");
            return;
        }
        PickTier tier;
        try {
            tier = PickTier.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cНеизвестный тир. Доступны: VETERAN, DWARF, GOBLIN.");
            return;
        }
        if (tier == PickTier.VANILLA) {
            sender.sendMessage("§cУ обычной кирки нет фрагмента.");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[3]);
        if (target == null) { sender.sendMessage("§cИгрок не найден."); return; }
        int amount = parseInt(args.length >= 5 ? args[4] : "1", 1);
        target.getInventory().addItem(pickService.createFragment(tier, amount));
        sender.sendMessage("§aВыдано " + amount + " × " + tier.fragmentDisplayName
                + " §aигроку " + target.getName());
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
        for (ru.raskol.mining.model.PendingOre p : store.pending()) {
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
        sender.sendMessage("§aКонфиг RaskolMining перезагружен.");
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("pick", "fragment", "golden", "list", "reload"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "pick", "fragment", "golden" -> filter(List.of("give"), args[1]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pick") && args[1].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("VANILLA", "VETERAN", "DWARF", "GOBLIN"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("fragment") && args[1].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("VETERAN", "DWARF", "GOBLIN"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}

package ru.office.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OfficeCommand implements CommandExecutor, TabCompleter {

    private final OfficePlugin plugin;

    public OfficeCommand(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.prefix + C.RED + "Только игроки могут использовать эту команду.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        TradeManager tm = plugin.getTradeManager();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "accept" -> tm.acceptRequest(player);
            case "cancel" -> tm.cancelByCommand(player);
            case "help" -> sendHelp(player);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(C.prefix + C.RED + "Игрок " + C.AQUA + args[0] + C.RED + " не найден или не в сети!");
                    return true;
                }
                tm.sendRequest(player, target);
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(C.BAR);
        player.sendMessage(C.prefix + C.BOLD + C.GOLD + "Команды Office Trade:");
        player.sendMessage(C.AQUA + " /office <ник>" + C.GRAY + " — отправить запрос на трейд");
        player.sendMessage(C.GREEN + " /office accept" + C.GRAY + " — принять входящий запрос");
        player.sendMessage(C.RED + " /office cancel" + C.GRAY + " — отменить запрос или трейд");
        player.sendMessage("");
        player.sendMessage(C.GOLD + " 💰 Комиссия:" + C.YELLOW + " " + OfficePlugin.getEconomy().format(TradeRequest.TRADE_COST));
        player.sendMessage(C.GRAY + " 💵 Вы можете добавить деньги в трейд через GUI");
        player.sendMessage(C.BAR);
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : Arrays.asList("accept", "cancel", "help")) {
                if (sub.startsWith(input)) completions.add(sub);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input) && !p.getName().equals(sender.getName())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}

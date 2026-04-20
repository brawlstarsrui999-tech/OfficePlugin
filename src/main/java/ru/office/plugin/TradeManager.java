package ru.office.plugin;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TradeManager {

    private final OfficePlugin plugin;

    // UUID отправителя -> запрос
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();
    // UUID любого из двух игроков -> сессия
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();

    public TradeManager(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────
    // ЗАПРОСЫ
    // ──────────────────────────────────────────────

    public void sendRequest(Player sender, Player target) {
        // Проверки
        if (sender.equals(target)) {
            sender.sendMessage(C.prefix + C.RED + "Нельзя отправить запрос самому себе!");
            return;
        }
        if (hasSession(sender.getUniqueId())) {
            sender.sendMessage(C.prefix + C.RED + "Вы уже находитесь в активном трейде!");
            return;
        }
        if (hasSession(target.getUniqueId())) {
            sender.sendMessage(C.prefix + C.RED + "Этот игрок уже находится в трейде!");
            return;
        }
        if (pendingRequests.containsKey(sender.getUniqueId())) {
            sender.sendMessage(C.prefix + C.RED + "У вас уже есть активный запрос! Используйте /office cancel");
            return;
        }

        // Проверка баланса
        Economy eco = OfficePlugin.getEconomy();
        if (!eco.has(sender, TradeRequest.TRADE_COST)) {
            sender.sendMessage(C.prefix + C.RED + "Недостаточно средств! Нужно " + C.GOLD + eco.format(TradeRequest.TRADE_COST));
            return;
        }

        TradeRequest req = new TradeRequest(sender, target);
        pendingRequests.put(sender.getUniqueId(), req);

        sender.sendMessage("");
        sender.sendMessage(C.BAR);
        sender.sendMessage(C.prefix + C.GREEN + "Запрос на трейд отправлен игроку " + C.AQUA + target.getName());
        sender.sendMessage(C.prefix + C.YELLOW + "Комиссия: " + C.GOLD + eco.format(TradeRequest.TRADE_COST) + C.YELLOW + " (спишется при подтверждении)");
        sender.sendMessage(C.BAR);
        sender.sendMessage("");

        target.sendMessage("");
        target.sendMessage(C.BAR);
        target.sendMessage(C.prefix + C.AQUA + sender.getName() + C.YELLOW + " хочет начать трейд с вами!");
        target.sendMessage(C.prefix + C.GRAY + "Напишите " + C.GREEN + "/office accept" + C.GRAY + " чтобы принять, или " + C.RED + "/office cancel" + C.GRAY + " чтобы отклонить.");
        target.sendMessage(C.prefix + C.YELLOW + "Комиссия отправителя: " + C.GOLD + eco.format(TradeRequest.TRADE_COST));
        target.sendMessage(C.BAR);
        target.sendMessage("");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        // Авто-отмена через 60 секунд
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(sender.getUniqueId())) {
                pendingRequests.remove(sender.getUniqueId());
                if (sender.isOnline()) sender.sendMessage(C.prefix + C.RED + "Запрос трейда истёк.");
                if (target.isOnline()) target.sendMessage(C.prefix + C.RED + "Запрос трейда от " + sender.getName() + " истёк.");
            }
        }, 1200L); // 60 сек
    }

    /** Целевой игрок принимает запрос (ищем запрос к нему). */
    public void acceptRequest(Player target) {
        TradeRequest req = findRequestFor(target.getUniqueId());
        if (req == null) {
            target.sendMessage(C.prefix + C.RED + "У вас нет входящих запросов на трейд!");
            return;
        }

        Player sender = Bukkit.getPlayer(req.getSenderUUID());
        if (sender == null || !sender.isOnline()) {
            pendingRequests.remove(req.getSenderUUID());
            target.sendMessage(C.prefix + C.RED + "Отправитель запроса вышел с сервера.");
            return;
        }

        // Проверяем баланс снова
        Economy eco = OfficePlugin.getEconomy();
        if (!eco.has(sender, TradeRequest.TRADE_COST)) {
            pendingRequests.remove(req.getSenderUUID());
            target.sendMessage(C.prefix + C.RED + "У отправителя недостаточно средств для трейда.");
            sender.sendMessage(C.prefix + C.RED + "Недостаточно средств — трейд отменён.");
            return;
        }

        pendingRequests.remove(req.getSenderUUID());

        // Создаём сессию
        TradeSession session = new TradeSession(
                req.getSenderUUID(), req.getSenderName(),
                req.getTargetUUID(), req.getTargetName()
        );
        Inventory gui = buildGUI(session);
        session.setInventory(gui);
        activeSessions.put(req.getSenderUUID(), session);
        activeSessions.put(req.getTargetUUID(), session);

        sender.openInventory(gui);
        target.openInventory(gui);

        sender.sendMessage("");
        sender.sendMessage(C.BAR);
        sender.sendMessage(C.prefix + C.GREEN + "Трейд с " + C.AQUA + target.getName() + C.GREEN + " начат!");
        sender.sendMessage(C.prefix + C.GRAY + "Положите предметы в " + C.YELLOW + "жёлтую зону" + C.GRAY + " и нажмите " + C.GREEN + "«Принять»");
        sender.sendMessage(C.BAR);
        sender.sendMessage("");

        target.sendMessage("");
        target.sendMessage(C.BAR);
        target.sendMessage(C.prefix + C.GREEN + "Трейд с " + C.AQUA + sender.getName() + C.GREEN + " начат!");
        target.sendMessage(C.prefix + C.GRAY + "Положите предметы в " + C.YELLOW + "жёлтую зону" + C.GRAY + " и нажмите " + C.GREEN + "«Принять»");
        target.sendMessage(C.BAR);
        target.sendMessage("");

        sender.playSound(sender.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
        target.playSound(target.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    /** Отмена: отправитель или получатель пишут /office cancel */
    public void cancelByCommand(Player player) {
        // Проверим, есть ли исходящий запрос
        if (pendingRequests.containsKey(player.getUniqueId())) {
            TradeRequest req = pendingRequests.remove(player.getUniqueId());
            player.sendMessage(C.prefix + C.YELLOW + "Запрос трейда отменён.");
            Player target = Bukkit.getPlayer(req.getTargetUUID());
            if (target != null) target.sendMessage(C.prefix + C.RED + player.getName() + " отменил запрос трейда.");
            return;
        }
        // Проверим входящий запрос
        TradeRequest req = findRequestFor(player.getUniqueId());
        if (req != null) {
            pendingRequests.remove(req.getSenderUUID());
            player.sendMessage(C.prefix + C.YELLOW + "Запрос трейда отклонён.");
            Player sender = Bukkit.getPlayer(req.getSenderUUID());
            if (sender != null) sender.sendMessage(C.prefix + C.RED + player.getName() + " отклонил ваш запрос трейда.");
            return;
        }
        // Активная сессия
        if (hasSession(player.getUniqueId())) {
            TradeSession session = activeSessions.get(player.getUniqueId());
            cancelSession(session, player.getName() + " отменил трейд.");
            return;
        }
        player.sendMessage(C.prefix + C.RED + "У вас нет активных запросов или трейдов.");
    }

    // ──────────────────────────────────────────────
    // GUI
    // ──────────────────────────────────────────────

    /**
     * Структура GUI (4 ряда = 36 слотов):
     *
     * Ряд 0 (0-8):   Декор — верхняя рамка (чёрное стекло)
     * Ряд 1 (9-17):  Слоты предметов (9 штук) — зона трейда  ← предметы игрока здесь
     * Ряд 2 (18-26): Декор — нижняя рамка (чёрное стекло)
     * Ряд 3 (27-35): Кнопки: [27]=«Принять» (синяя орхидея), [35]=«Отмена» (красный мак),
     *                остальные — украшения
     *
     * Итого 4 ряда × 9 = 36 слотов
     */
    public Inventory buildGUI(TradeSession session) {
        String title = buildTitle(session, false);
        Inventory inv = Bukkit.createInventory(null, 36, title);
        refreshGUI(session, inv, false);
        return inv;
    }

    public void refreshGUI(TradeSession session, Inventory inv, boolean confirmMode) {
        // Сохраняем предметы из зоны трейда (слоты 9-17)
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack existing = inv.getItem(9 + i);
            if (existing != null
                    && existing.getType() != Material.AIR
                    && existing.getType() != Material.YELLOW_STAINED_GLASS_PANE
                    && existing.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                saved[i] = existing;
            }
        }

        // Очищаем весь инвентарь
        inv.clear();

        // Ряд 0 (0-8): декор — чёрное стекло
        ItemStack blackPane = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, blackPane);
        }

        // Ряд 1 (9-17): зона трейда
        ItemStack tradeSlot = confirmMode
                ? makeItem(Material.LIME_STAINED_GLASS_PANE, C.GRAY + "Слот подтверждения")
                : makeItem(Material.YELLOW_STAINED_GLASS_PANE, C.GRAY + "Положите предмет сюда");
        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, tradeSlot);
        }

        // Восстанавливаем сохранённые предметы
        for (int i = 0; i < 9; i++) {
            if (saved[i] != null) {
                inv.setItem(9 + i, saved[i]);
            }
        }

        // Ряд 2 (18-26): декор — чёрное стекло
        for (int i = 18; i <= 26; i++) {
            inv.setItem(i, blackPane);
        }

        // Ряд 3 (27-35): кнопки
        ItemStack acceptBtn;
        if (confirmMode) {
            acceptBtn = makeItem(Material.BLUE_ORCHID,
                    C.GREEN + "" + ChatColor.BOLD + "✔ ПРИНЯТЬ ТРЕЙД",
                    C.GRAY + "Оба игрока подтвердили.",
                    C.YELLOW + "Трейд завершится автоматически!");
        } else {
            acceptBtn = makeItem(Material.BLUE_ORCHID,
                    C.GREEN + "" + ChatColor.BOLD + "✔ Принять",
                    C.GRAY + "Нажмите, чтобы подтвердить трейд.",
                    C.YELLOW + "Ждём подтверждения второго игрока.");
        }
        inv.setItem(27, acceptBtn);

        ItemStack cancelBtn = makeItem(Material.POPPY,
                C.RED + "" + ChatColor.BOLD + "✗ Отмена",
                C.GRAY + "Отменить трейд и вернуть предметы.");
        inv.setItem(35, cancelBtn);

        // Декор ряд 3 (28-34, кроме 27 и 35)
        ItemStack grayPane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 28; i <= 34; i++) {
            inv.setItem(i, grayPane);
        }

        // Статус подтверждения
        String senderStatus = session.isSenderConfirmed() ? C.GREEN + "✔ Подтверждено" : C.RED + "✗ Ожидание";
        String targetStatus = session.isTargetConfirmed() ? C.GREEN + "✔ Подтверждено" : C.RED + "✗ Ожидание";
        ItemStack statusItem = makeItem(Material.PAPER,
                C.YELLOW + "Статус подтверждения",
                C.AQUA + session.getSenderName() + ": " + senderStatus,
                C.AQUA + session.getTargetName() + ": " + targetStatus);
        inv.setItem(31, statusItem);
    }

    private String buildTitle(TradeSession session, boolean confirmMode) {
        return ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ OFFICE TRADE ✦";
    }

    public void handleButtonClick(Player player, TradeSession session, int slot) {
        if (slot == 35) {
            // Кнопка «Отмена»
            cancelSession(session, player.getName() + " отменил трейд.");
            return;
        }

        if (slot == 27) {
            // Кнопка «Принять»
            if (session.getState() == TradeSession.State.CONFIRMING) {
                player.sendMessage(C.prefix + C.YELLOW + "Вы уже подтвердили трейд! Ожидаем второго игрока...");
                return;
            }
            if (session.getState() != TradeSession.State.OPEN) return;

            boolean isSender = player.getUniqueId().equals(session.getSenderUUID());
            if (isSender) {
                session.setSenderConfirmed(true);
            } else {
                session.setTargetConfirmed(true);
            }

            player.sendMessage(C.prefix + C.GREEN + "Вы подтвердили трейд! Ожидаем второго игрока...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);

            // Обновляем GUI для обоих
            refreshAfterChange(session);

            if (session.bothConfirmed()) {
                session.setState(TradeSession.State.CONFIRMING);
                startConfirmTimer(session);
            }
        }
    }

    public void refreshAfterChange(TradeSession session) {
        Inventory inv = session.getInventory();
        if (inv == null) return;
        boolean confirmMode = session.isSenderConfirmed() || session.isTargetConfirmed();
        refreshGUI(session, inv, confirmMode);
    }

    private void startConfirmTimer(TradeSession session) {
        Player s = Bukkit.getPlayer(session.getSenderUUID());
        Player t = Bukkit.getPlayer(session.getTargetUUID());

        if (s != null) {
            s.sendMessage("");
            s.sendMessage(C.BAR);
            s.sendMessage(C.prefix + C.GREEN + "" + ChatColor.BOLD + "Оба подтвердили! Трейд завершится через 5 сек...");
            s.sendMessage(C.BAR);
            s.sendMessage("");
        }
        if (t != null) {
            t.sendMessage("");
            t.sendMessage(C.BAR);
            t.sendMessage(C.prefix + C.GREEN + "" + ChatColor.BOLD + "Оба подтвердили! Трейд завершится через 5 сек...");
            t.sendMessage(C.BAR);
            t.sendMessage("");
        }

        final int[] remaining = {5};
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.getState() != TradeSession.State.CONFIRMING) {
                    cancel();
                    return;
                }
                remaining[0]--;
                Player sender = Bukkit.getPlayer(session.getSenderUUID());
                Player target = Bukkit.getPlayer(session.getTargetUUID());
                if (remaining[0] > 0) {
                    if (sender != null) {
                        sender.sendMessage(C.prefix + C.YELLOW + "Трейд завершится через " + C.WHITE + remaining[0] + C.YELLOW + " сек...");
                        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f);
                    }
                    if (target != null) {
                        target.sendMessage(C.prefix + C.YELLOW + "Трейд завершится через " + C.WHITE + remaining[0] + C.YELLOW + " сек...");
                        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f);
                    }
                } else {
                    cancel();
                    completeTrade(session);
                }
            }
        };
        int taskId = task.runTaskTimer(plugin, 20L, 20L).getTaskId();
        session.setTimerTaskId(taskId);
    }

    // ──────────────────────────────────────────────
    // ЗАВЕРШЕНИЕ ТРЕЙДА
    // ──────────────────────────────────────────────

    private void completeTrade(TradeSession session) {
        if (session.getState() == TradeSession.State.COMPLETED
                || session.getState() == TradeSession.State.CANCELLED) return;
        session.setState(TradeSession.State.COMPLETED);

        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());

        // Закрываем GUI
        if (sender != null) sender.closeInventory();
        if (target != null) target.closeInventory();

        // Списываем деньги с отправителя
        Economy eco = OfficePlugin.getEconomy();
        boolean charged = false;
        if (sender != null && eco.has(sender, TradeRequest.TRADE_COST)) {
            EconomyResponse resp = eco.withdrawPlayer(sender, TradeRequest.TRADE_COST);
            charged = resp.transactionSuccess();
        } else if (sender == null) {
            // Если отправитель вышел — тоже пробуем списать по OfflinePlayer
            org.bukkit.OfflinePlayer offSender = Bukkit.getOfflinePlayer(session.getSenderUUID());
            EconomyResponse resp = eco.withdrawPlayer(offSender, TradeRequest.TRADE_COST);
            charged = resp.transactionSuccess();
        }

        // Передаём предметы получателю.
        // getTradeItems() теперь корректно читает слоты 9-17 (исправлено в TradeSession).
        ItemStack[] tradeItems = session.getTradeItems();
        if (target != null) {
            for (ItemStack item : tradeItems) {
                if (item != null && item.getType() != Material.AIR) {
                    Map<Integer, ItemStack> leftover = target.getInventory().addItem(item.clone());
                    // Если инвентарь полный — дроп на землю
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                    }
                }
            }
        }

        // Сообщения
        String chargedStr = charged ? eco.format(TradeRequest.TRADE_COST) : "ОШИБКА";
        if (sender != null) {
            sender.sendMessage("");
            sender.sendMessage(C.BAR);
            sender.sendMessage(C.prefix + C.GREEN + "" + ChatColor.BOLD + "✔ ТРЕЙД ЗАВЕРШЁН!");
            sender.sendMessage(C.prefix + C.YELLOW + "Списано: " + C.GOLD + eco.format(TradeRequest.TRADE_COST));
            sender.sendMessage(C.prefix + C.GRAY + "Предметы переданы: " + C.AQUA + (target != null ? target.getName() : session.getTargetName()));
            sender.sendMessage(C.BAR);
            sender.sendMessage("");
            sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }
        if (target != null) {
            target.sendMessage("");
            target.sendMessage(C.BAR);
            target.sendMessage(C.prefix + C.GREEN + "" + ChatColor.BOLD + "✔ ТРЕЙД ЗАВЕРШЁН!");
            target.sendMessage(C.prefix + C.YELLOW + "Вы получили предметы от: " + C.AQUA + session.getSenderName());
            target.sendMessage(C.BAR);
            target.sendMessage("");
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }

        plugin.getLogger().info("[OfficeTrade] Трейд завершён: " + session.getSenderName()
                + " -> " + session.getTargetName()
                + " | Списано: " + TradeRequest.TRADE_COST);

        // Удаляем сессию
        activeSessions.remove(session.getSenderUUID());
        activeSessions.remove(session.getTargetUUID());
    }

    // ──────────────────────────────────────────────
    // ОТМЕНА СЕССИИ
    // ──────────────────────────────────────────────

    public void cancelSession(TradeSession session, String reason) {
        if (session.getState() == TradeSession.State.CANCELLED
                || session.getState() == TradeSession.State.COMPLETED) return;
        session.setState(TradeSession.State.CANCELLED);

        // Отменяем таймер
        if (session.getTimerTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(session.getTimerTaskId());
            session.setTimerTaskId(-1);
        }

        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());

        // Возвращаем предметы из слотов 9-17 отправителю (или дроп)
        Inventory inv = session.getInventory();
        if (inv != null) {
            for (int i = 9; i <= 17; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR
                        && item.getType() != Material.YELLOW_STAINED_GLASS_PANE
                        && item.getType() != Material.BLACK_STAINED_GLASS_PANE
                        && item.getType() != Material.LIME_STAINED_GLASS_PANE) {
                    if (sender != null) {
                        Map<Integer, ItemStack> leftover = sender.getInventory().addItem(item.clone());
                        if (!leftover.isEmpty()) {
                            for (ItemStack drop : leftover.values()) {
                                sender.getWorld().dropItemNaturally(sender.getLocation(), drop);
                            }
                        }
                    }
                }
            }
        }

        if (sender != null) {
            sender.closeInventory();
            sender.sendMessage("");
            sender.sendMessage(C.BAR);
            sender.sendMessage(C.prefix + C.RED + "" + ChatColor.BOLD + "✗ ТРЕЙД ОТМЕНЁН");
            sender.sendMessage(C.prefix + C.GRAY + reason);
            sender.sendMessage(C.prefix + C.YELLOW + "Деньги не были списаны.");
            sender.sendMessage(C.BAR);
            sender.sendMessage("");
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        if (target != null) {
            target.closeInventory();
            target.sendMessage("");
            target.sendMessage(C.BAR);
            target.sendMessage(C.prefix + C.RED + "" + ChatColor.BOLD + "✗ ТРЕЙД ОТМЕНЁН");
            target.sendMessage(C.prefix + C.GRAY + reason);
            target.sendMessage(C.BAR);
            target.sendMessage("");
            target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        activeSessions.remove(session.getSenderUUID());
        activeSessions.remove(session.getTargetUUID());
    }

    public void cancelAllTrades() {
        Set<TradeSession> processed = new HashSet<>();
        for (TradeSession session : activeSessions.values()) {
            if (processed.contains(session)) continue;
            processed.add(session);
            cancelSession(session, "Сервер был перезапущен.");
        }
        activeSessions.clear();
        pendingRequests.clear();
    }

    // ──────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ
    // ──────────────────────────────────────────────

    public boolean hasSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    public TradeSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    private TradeRequest findRequestFor(UUID targetUUID) {
        for (TradeRequest req : pendingRequests.values()) {
            if (req.getTargetUUID().equals(targetUUID)) return req;
        }
        return null;
    }

    /** Создаёт красивый ItemStack с именем и лором. */
    public static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.RESET + name);
        if (lore.length > 0) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.RESET + line);
            }
            meta.setLore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Является ли слот кликабельным (зона предметов 9-17). */
    public boolean isTradeSlot(int slot) {
        return slot >= 9 && slot <= 17;
    }

    /** Является ли слот кнопкой или декором (не зона предметов). */
    public boolean isDecorSlot(int slot) {
        return !isTradeSlot(slot);
    }
}

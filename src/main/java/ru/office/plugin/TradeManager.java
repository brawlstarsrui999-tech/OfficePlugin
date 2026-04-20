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

    // ═══════════════════════════════════════════
    //  GUI КОНСТАНТЫ
    // ═══════════════════════════════════════════

    public static final int GUI_SIZE = 54;
    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ OFFICE TRADE ✦";

    // Зоны предметов
    public static final int SENDER_ZONE_START = 9;
    public static final int SENDER_ZONE_END   = 17;
    public static final int TARGET_ZONE_START = 27;
    public static final int TARGET_ZONE_END   = 35;

    // Деньги — ряд 2 (18-26)
    public static final int SENDER_MONEY_MINUS  = 18;
    public static final int SENDER_MONEY_DISPLAY = 19;
    public static final int SENDER_MONEY_PLUS   = 20;
    public static final int SEPARATOR_LEFT      = 21;
    public static final int CENTER_ARROW        = 22;
    public static final int SEPARATOR_RIGHT     = 23;
    public static final int TARGET_MONEY_MINUS  = 24;
    public static final int TARGET_MONEY_DISPLAY = 25;
    public static final int TARGET_MONEY_PLUS   = 26;

    // Статусы — ряд 4 (36-44)
    public static final int SENDER_STATUS = 39;
    public static final int TARGET_STATUS = 41;

    // Кнопки — ряд 5 (45-53)
    public static final int CONFIRM_BUTTON = 48;
    public static final int CANCEL_BUTTON  = 50;

    // Placeholder материалы
    public static final Material SENDER_PLACEHOLDER_MAT = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    public static final Material TARGET_PLACEHOLDER_MAT = Material.PINK_STAINED_GLASS_PANE;

    // ═══════════════════════════════════════════
    //  ХРАНИЛИЩА
    // ═══════════════════════════════════════════

    /** UUID отправителя → запрос */
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();

    /** UUID любого из двух игроков → сессия */
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();

    public TradeManager(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════
    //  ЗАПРОСЫ
    // ═══════════════════════════════════════════

    public void sendRequest(Player sender, Player target) {
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
        sender.sendMessage(C.prefix + C.YELLOW + "Комиссия: " + C.GOLD + eco.format(TradeRequest.TRADE_COST) + C.YELLOW + " (спишется при завершении)");
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
        }, 1200L);
    }

    /** Целевой игрок принимает запрос. */
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

        Economy eco = OfficePlugin.getEconomy();
        if (!eco.has(sender, TradeRequest.TRADE_COST)) {
            pendingRequests.remove(req.getSenderUUID());
            target.sendMessage(C.prefix + C.RED + "У отправителя недостаточно средств для трейда.");
            sender.sendMessage(C.prefix + C.RED + "Недостаточно средств — трейд отменён.");
            return;
        }

        pendingRequests.remove(req.getSenderUUID());

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
        sender.sendMessage(C.prefix + C.GREEN + "Трейд с " + C.LIGHT_PURPLE + target.getName() + C.GREEN + " начат!");
        sender.sendMessage(C.prefix + C.AQUA + "Кладите предметы в " + C.YELLOW + "верхнюю зону (голубые слоты)");
        sender.sendMessage(C.prefix + C.GRAY + "Нажмите " + C.GREEN + "«Подтвердить»" + C.GRAY + ", когда будете готовы");
        sender.sendMessage(C.BAR);
        sender.sendMessage("");

        target.sendMessage("");
        target.sendMessage(C.BAR);
        target.sendMessage(C.prefix + C.GREEN + "Трейд с " + C.AQUA + sender.getName() + C.GREEN + " начат!");
        target.sendMessage(C.prefix + C.LIGHT_PURPLE + "Кладите предметы в " + C.YELLOW + "нижнюю зону (розовые слоты)");
        target.sendMessage(C.prefix + C.GRAY + "Нажмите " + C.GREEN + "«Подтвердить»" + C.GRAY + ", когда будете готовы");
        target.sendMessage(C.BAR);
        target.sendMessage("");

        sender.playSound(sender.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
        target.playSound(target.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    /** Отмена: отправитель или получатель пишут /office cancel */
    public void cancelByCommand(Player player) {
        // Исходящий запрос?
        if (pendingRequests.containsKey(player.getUniqueId())) {
            TradeRequest req = pendingRequests.remove(player.getUniqueId());
            player.sendMessage(C.prefix + C.YELLOW + "Запрос трейда отменён.");
            Player target = Bukkit.getPlayer(req.getTargetUUID());
            if (target != null) target.sendMessage(C.prefix + C.RED + player.getName() + " отменил запрос трейда.");
            return;
        }

        // Входящий запрос?
        TradeRequest req = findRequestFor(player.getUniqueId());
        if (req != null) {
            pendingRequests.remove(req.getSenderUUID());
            player.sendMessage(C.prefix + C.YELLOW + "Запрос трейда отклонён.");
            Player sender = Bukkit.getPlayer(req.getSenderUUID());
            if (sender != null) sender.sendMessage(C.prefix + C.RED + player.getName() + " отклонил ваш запрос трейда.");
            return;
        }

        // Активная сессия?
        if (hasSession(player.getUniqueId())) {
            TradeSession session = activeSessions.get(player.getUniqueId());
            cancelSession(session, player.getName() + " отменил трейд.");
            return;
        }

        player.sendMessage(C.prefix + C.RED + "У вас нет активных запросов или трейдов.");
    }

    // ═══════════════════════════════════════════
    //  GUI ПОСТРОЕНИЕ
    // ═══════════════════════════════════════════

    public Inventory buildGUI(TradeSession session) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        fillGUI(session, inv);
        return inv;
    }

    /**
     * Полностью заполняет GUI декоративными элементами,
     * сохраняя реальные предметы из торговых зон.
     */
    public void refreshGUI(TradeSession session, Inventory inv) {
        fillGUI(session, inv);
    }

    private void fillGUI(TradeSession session, Inventory inv) {
        // 1) Сохраняем реальные предметы из зон
        List<SavedItem> savedItems = new ArrayList<>();
        saveItemsFromZone(inv, SENDER_ZONE_START, SENDER_ZONE_END, savedItems);
        saveItemsFromZone(inv, TARGET_ZONE_START, TARGET_ZONE_END, savedItems);

        // 2) Очищаем
        inv.clear();

        // 3) Ряд 0 — Верхняя рамка
        fillRow(inv, 0, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(2, makeItem(Material.PLAYER_HEAD,
                C.AQUA + "← " + session.getSenderName(),
                C.GRAY + "Верхняя зона предметов"));
        inv.setItem(4, makeItem(Material.NETHER_STAR,
                C.DARK_PURPLE + "" + C.BOLD + "✦ OFFICE TRADE ✦",
                C.GRAY + "Обмен предметами и деньгами",
                "",
                C.AQUA + "Верх: " + session.getSenderName(),
                C.LIGHT_PURPLE + "Низ: " + session.getTargetName()));
        inv.setItem(6, makeItem(Material.PLAYER_HEAD,
                C.LIGHT_PURPLE + session.getTargetName() + " →",
                C.GRAY + "Нижняя зона предметов"));

        // 4) Ряд 1 — Зона предметов отправителя
        for (int i = SENDER_ZONE_START; i <= SENDER_ZONE_END; i++) {
            inv.setItem(i, makePlaceholder(SENDER_PLACEHOLDER_MAT,
                    C.AQUA + "Предмет " + (i - SENDER_ZONE_START + 1)));
        }

        // 5) Ряд 2 — Деньги и разделитель
        fillRow(inv, 2, Material.GRAY_STAINED_GLASS_PANE);

        Economy eco = OfficePlugin.getEconomy();
        String senderMoneyStr = eco != null ? eco.format(session.getSenderMoney()) : "$" + String.format("%.0f", session.getSenderMoney());
        String targetMoneyStr = eco != null ? eco.format(session.getTargetMoney()) : "$" + String.format("%.0f", session.getTargetMoney());

        inv.setItem(SENDER_MONEY_MINUS, makeItem(Material.RED_CONCRETE,
                C.RED + "▼ Убавить",
                C.GRAY + "ЛКМ: −$100",
                C.GRAY + "ПКМ: −$10"));
        inv.setItem(SENDER_MONEY_DISPLAY, makeItem(Material.GOLD_BLOCK,
                C.GOLD + "💰 " + senderMoneyStr,
                C.AQUA + session.getSenderName() + " предлагает",
                "",
                C.GRAY + "ЛКМ: +$100  |  ПКМ: +$10",
                C.GRAY + "Shift+ЛКМ: +$1000",
                C.GRAY + "Shift+ПКМ: +$1"));
        inv.setItem(SENDER_MONEY_PLUS, makeItem(Material.GREEN_CONCRETE,
                C.GREEN + "▲ Добавить",
                C.GRAY + "ЛКМ: +$100",
                C.GRAY + "ПКМ: +$10"));

        inv.setItem(CENTER_ARROW, makeItem(Material.ARROW,
                C.YELLOW + "⇄ Обмен ⇄",
                C.GRAY + "Предметы и деньги",
                C.GRAY + "поменяются местами",
                "",
                C.AQUA + session.getSenderName() + ": " + senderMoneyStr,
                C.LIGHT_PURPLE + session.getTargetName() + ": " + targetMoneyStr));

        inv.setItem(TARGET_MONEY_MINUS, makeItem(Material.RED_CONCRETE,
                C.RED + "▼ Убавить",
                C.GRAY + "ЛКМ: −$100",
                C.GRAY + "ПКМ: −$10"));
        inv.setItem(TARGET_MONEY_DISPLAY, makeItem(Material.GOLD_BLOCK,
                C.GOLD + "💰 " + targetMoneyStr,
                C.LIGHT_PURPLE + session.getTargetName() + " предлагает",
                "",
                C.GRAY + "ЛКМ: +$100  |  ПКМ: +$10",
                C.GRAY + "Shift+ЛКМ: +$1000",
                C.GRAY + "Shift+ПКМ: +$1"));
        inv.setItem(TARGET_MONEY_PLUS, makeItem(Material.GREEN_CONCRETE,
                C.GREEN + "▲ Добавить",
                C.GRAY + "ЛКМ: +$100",
                C.GRAY + "ПКМ: +$10"));

        // 6) Ряд 3 — Зона предметов получателя
        for (int i = TARGET_ZONE_START; i <= TARGET_ZONE_END; i++) {
            inv.setItem(i, makePlaceholder(TARGET_PLACEHOLDER_MAT,
                    C.LIGHT_PURPLE + "Предмет " + (i - TARGET_ZONE_START + 1)));
        }

        // 7) Ряд 4 — Статусы
        fillRow(inv, 4, Material.GRAY_STAINED_GLASS_PANE);

        if (session.isSenderConfirmed()) {
            inv.setItem(SENDER_STATUS, makeItem(Material.LIME_CONCRETE,
                    C.AQUA + session.getSenderName(),
                    C.GREEN + "" + C.BOLD + "✔ Подтверждено"));
        } else {
            inv.setItem(SENDER_STATUS, makeItem(Material.YELLOW_CONCRETE,
                    C.AQUA + session.getSenderName(),
                    C.YELLOW + "⏳ Ожидание подтверждения..."));
        }

        if (session.isTargetConfirmed()) {
            inv.setItem(TARGET_STATUS, makeItem(Material.LIME_CONCRETE,
                    C.LIGHT_PURPLE + session.getTargetName(),
                    C.GREEN + "" + C.BOLD + "✔ Подтверждено"));
        } else {
            inv.setItem(TARGET_STATUS, makeItem(Material.YELLOW_CONCRETE,
                    C.LIGHT_PURPLE + session.getTargetName(),
                    C.YELLOW + "⏳ Ожидание подтверждения..."));
        }

        // 8) Ряд 5 — Кнопки
        fillRow(inv, 5, Material.GRAY_STAINED_GLASS_PANE);

        if (session.getState() == TradeSession.State.CONFIRMING) {
            inv.setItem(CONFIRM_BUTTON, makeItem(Material.ORANGE_CONCRETE,
                    C.GOLD + "" + C.BOLD + "⏳ Обмен...",
                    C.YELLOW + "Идёт обратный отсчёт"));
        } else {
            inv.setItem(CONFIRM_BUTTON, makeItem(Material.LIME_CONCRETE,
                    C.GREEN + "" + C.BOLD + "✔ ПОДТВЕРДИТЬ",
                    C.GRAY + "Нажмите для подтверждения",
                    "",
                    C.YELLOW + "Повторный клик снимает",
                    C.YELLOW + "подтверждение"));
        }

        inv.setItem(CANCEL_BUTTON, makeItem(Material.RED_CONCRETE,
                C.RED + "" + C.BOLD + "✗ ОТМЕНА",
                C.GRAY + "Отменить трейд и",
                C.GRAY + "вернуть все предметы"));

        // 9) Восстанавливаем сохранённые предметы
        for (SavedItem si : savedItems) {
            inv.setItem(si.slot, si.item);
        }
    }

    // ═══════════════════════════════════════════
    //  ОБРАБОТКА КНОПОК
    // ═══════════════════════════════════════════

    /** Игрок нажал кнопку «Подтвердить». */
    public void handleConfirmClick(Player player, TradeSession session) {
        if (session.getState() == TradeSession.State.CONFIRMING) {
            player.sendMessage(C.prefix + C.GOLD + "Обмен уже подтверждён! Идёт отсчёт...");
            return;
        }
        if (session.getState() != TradeSession.State.OPEN) return;

        boolean isSender = player.getUniqueId().equals(session.getSenderUUID());

        if (isSender) {
            if (session.isSenderConfirmed()) {
                session.setSenderConfirmed(false);
                player.sendMessage(C.prefix + C.YELLOW + "Вы сняли подтверждение.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            } else {
                session.setSenderConfirmed(true);
                player.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ Вы подтвердили трейд!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        } else {
            if (session.isTargetConfirmed()) {
                session.setTargetConfirmed(false);
                player.sendMessage(C.prefix + C.YELLOW + "Вы сняли подтверждение.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            } else {
                session.setTargetConfirmed(true);
                player.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ Вы подтвердили трейд!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        }

        refreshGUI(session, session.getInventory());

        if (session.bothConfirmed()) {
            startConfirmationCountdown(session);
        }
    }

    /** Обработка кликов по кнопкам денег. */
    public void handleMoneyClick(TradeSession session, boolean isSender, int slot, org.bukkit.event.inventory.ClickType click) {
        if (session.getState() != TradeSession.State.OPEN) return;

        // Клик по дисплею тоже работает как +
        boolean isPlus = (slot == SENDER_MONEY_PLUS || slot == TARGET_MONEY_PLUS || slot == SENDER_MONEY_DISPLAY || slot == TARGET_MONEY_DISPLAY);
        boolean isMinus = (slot == SENDER_MONEY_MINUS || slot == TARGET_MONEY_MINUS);

        if (!isPlus && !isMinus) return;

        double amount = 0;
        if (click == org.bukkit.event.inventory.ClickType.LEFT)          amount = 100;
        else if (click == org.bukkit.event.inventory.ClickType.RIGHT)    amount = 10;
        else if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT)  amount = 1000;
        else if (click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) amount = 1;

        if (amount == 0) return;

        // Для кнопки-дислея всегда прибавляем
        if (slot == SENDER_MONEY_DISPLAY || slot == TARGET_MONEY_DISPLAY) {
            // already isPlus
        }

        if (isMinus) amount = -amount;

        if (isSender) {
            double newMoney = Math.max(0, session.getSenderMoney() + amount);
            session.setSenderMoney(newMoney);
        } else {
            double newMoney = Math.max(0, session.getTargetMoney() + amount);
            session.setTargetMoney(newMoney);
        }

        session.resetConfirmations();
        refreshGUI(session, session.getInventory());

        Player player = Bukkit.getPlayer(isSender ? session.getSenderUUID() : session.getTargetUUID());
        if (player != null) {
            double current = isSender ? session.getSenderMoney() : session.getTargetMoney();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
            player.sendMessage(C.prefix + C.GOLD + "Ваша сумма: " + C.YELLOW + formatMoney(current));
        }
    }

    // ═══════════════════════════════════════════
    //  ПОДТВЕРЖДЕНИЕ И ЗАВЕРШЕНИЕ
    // ═══════════════════════════════════════════

    private void startConfirmationCountdown(TradeSession session) {
        session.setState(TradeSession.State.CONFIRMING);
        refreshGUI(session, session.getInventory());

        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());

        if (sender != null) {
            sender.sendMessage("");
            sender.sendMessage(C.BAR);
            sender.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ Оба подтвердили! Обмен через 5 секунд...");
            sender.sendMessage(C.BAR);
            sender.sendMessage("");
            sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
        if (target != null) {
            target.sendMessage("");
            target.sendMessage(C.BAR);
            target.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ Оба подтвердили! Обмен через 5 секунд...");
            target.sendMessage(C.BAR);
            target.sendMessage("");
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }

        final int[] countdown = {5};
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                countdown[0]--;
                if (countdown[0] > 0) {
                    if (sender != null) {
                        sender.sendMessage(C.prefix + C.YELLOW + "Обмен через " + C.WHITE + countdown[0] + C.YELLOW + " сек...");
                        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f);
                    }
                    if (target != null) {
                        target.sendMessage(C.prefix + C.YELLOW + "Обмен через " + C.WHITE + countdown[0] + C.YELLOW + " сек...");
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

    private void completeTrade(TradeSession session) {
        if (session.getState() == TradeSession.State.COMPLETED || session.getState() == TradeSession.State.CANCELLED) return;
        session.setState(TradeSession.State.COMPLETED);

        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());

        // Закрываем GUI
        if (sender != null) sender.closeInventory();
        if (target != null) target.closeInventory();

        Economy eco = OfficePlugin.getEconomy();

        // Проверка наличия игроков
        if (sender == null || target == null) {
            // Возвращаем предметы через cancel (но ставим COMPLETED чтобы cancelSession не меняла состояние)
            session.setState(TradeSession.State.CANCELLED);
            returnItemsToOwners(session);
            activeSessions.remove(session.getSenderUUID());
            activeSessions.remove(session.getTargetUUID());
            return;
        }

        // Проверка денег
        double senderNeeds = session.getSenderMoney() + TradeRequest.TRADE_COST;
        double targetNeeds = session.getTargetMoney();

        if (!eco.has(sender, senderNeeds)) {
            session.setState(TradeSession.State.CANCELLED);
            returnItemsToOwners(session);
            sender.sendMessage(C.prefix + C.RED + "Недостаточно средств! Нужно " + C.GOLD + eco.format(senderNeeds));
            target.sendMessage(C.prefix + C.RED + "У " + session.getSenderName() + " недостаточно средств.");
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            activeSessions.remove(session.getSenderUUID());
            activeSessions.remove(session.getTargetUUID());
            return;
        }

        if (!eco.has(target, targetNeeds)) {
            session.setState(TradeSession.State.CANCELLED);
            returnItemsToOwners(session);
            target.sendMessage(C.prefix + C.RED + "Недостаточно средств! Нужно " + C.GOLD + eco.format(targetNeeds));
            sender.sendMessage(C.prefix + C.RED + "У " + session.getTargetName() + " недостаточно средств.");
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            activeSessions.remove(session.getSenderUUID());
            activeSessions.remove(session.getTargetUUID());
            return;
        }

        // ── Передаём предметы: отправитель → получатель ──
        List<ItemStack> senderItems = session.getSenderItems();
        for (ItemStack item : senderItems) {
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
        }

        // ── Передаём предметы: получатель → отправитель ──
        List<ItemStack> targetItems = session.getTargetItems();
        for (ItemStack item : targetItems) {
            HashMap<Integer, ItemStack> leftover = sender.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                sender.getWorld().dropItemNaturally(sender.getLocation(), drop);
            }
        }

        // ── Перевод денег ──
        if (session.getSenderMoney() > 0) {
            eco.withdrawPlayer(sender, session.getSenderMoney());
            eco.depositPlayer(target, session.getSenderMoney());
        }
        if (session.getTargetMoney() > 0) {
            eco.withdrawPlayer(target, session.getTargetMoney());
            eco.depositPlayer(sender, session.getTargetMoney());
        }

        // ── Комиссия ──
        eco.withdrawPlayer(sender, TradeRequest.TRADE_COST);

        // ── Сообщения ──
        String senderMoneyStr = formatMoney(session.getSenderMoney());
        String targetMoneyStr = formatMoney(session.getTargetMoney());

        sender.sendMessage("");
        sender.sendMessage(C.BAR);
        sender.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ ТРЕЙД ЗАВЕРШЁН!");
        sender.sendMessage(C.prefix + C.YELLOW + "Комиссия: " + C.GOLD + formatMoney(TradeRequest.TRADE_COST));
        if (session.getSenderMoney() > 0)
            sender.sendMessage(C.prefix + C.GOLD + "Вы отправили: " + C.YELLOW + senderMoneyStr);
        if (session.getTargetMoney() > 0)
            sender.sendMessage(C.prefix + C.GOLD + "Вы получили: " + C.YELLOW + targetMoneyStr);
        sender.sendMessage(C.prefix + C.GRAY + "Предметы переданы: " + C.LIGHT_PURPLE + session.getTargetName());
        sender.sendMessage(C.BAR);
        sender.sendMessage("");
        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        target.sendMessage("");
        target.sendMessage(C.BAR);
        target.sendMessage(C.prefix + C.GREEN + "" + C.BOLD + "✔ ТРЕЙД ЗАВЕРШЁН!");
        if (session.getTargetMoney() > 0)
            target.sendMessage(C.prefix + C.GOLD + "Вы отправили: " + C.YELLOW + targetMoneyStr);
        if (session.getSenderMoney() > 0)
            target.sendMessage(C.prefix + C.GOLD + "Вы получили: " + C.YELLOW + senderMoneyStr);
        target.sendMessage(C.prefix + C.GRAY + "Предметы получены от: " + C.AQUA + session.getSenderName());
        target.sendMessage(C.BAR);
        target.sendMessage("");
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        plugin.getLogger().info("[OfficeTrade] Трейд завершён: " + session.getSenderName() + " -> " + session.getTargetName()
                + " | Предметов: " + senderItems.size() + " <-> " + targetItems.size()
                + " | Деньги: " + senderMoneyStr + " <-> " + targetMoneyStr);

        activeSessions.remove(session.getSenderUUID());
        activeSessions.remove(session.getTargetUUID());
    }

    // ═══════════════════════════════════════════
    //  ОТМЕНА СЕССИИ
    // ═══════════════════════════════════════════

    public void cancelSession(TradeSession session, String reason) {
        if (session.getState() == TradeSession.State.CANCELLED || session.getState() == TradeSession.State.COMPLETED) return;
        session.setState(TradeSession.State.CANCELLED);

        // Отменяем таймер
        if (session.getTimerTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(session.getTimerTaskId());
            session.setTimerTaskId(-1);
        }

        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());

        // Возвращаем предметы владельцам
        returnItemsToOwners(session);

        // Закрываем GUI
        if (sender != null) sender.closeInventory();
        if (target != null) target.closeInventory();

        // ── Сообщения ──
        if (sender != null) {
            sender.sendMessage("");
            sender.sendMessage(C.BAR);
            sender.sendMessage(C.prefix + C.RED + "" + C.BOLD + "✗ ТРЕЙД ОТМЕНЁН");
            sender.sendMessage(C.prefix + C.GRAY + reason);
            sender.sendMessage(C.prefix + C.GREEN + "Все ваши предметы возвращены.");
            sender.sendMessage(C.prefix + C.YELLOW + "Деньги не были списаны.");
            sender.sendMessage(C.BAR);
            sender.sendMessage("");
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        if (target != null) {
            target.sendMessage("");
            target.sendMessage(C.BAR);
            target.sendMessage(C.prefix + C.RED + "" + C.BOLD + "✗ ТРЕЙД ОТМЕНЁН");
            target.sendMessage(C.prefix + C.GRAY + reason);
            target.sendMessage(C.prefix + C.GREEN + "Все ваши предметы возвращены.");
            target.sendMessage(C.prefix + C.YELLOW + "Деньги не были списаны.");
            target.sendMessage(C.BAR);
            target.sendMessage("");
            target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        activeSessions.remove(session.getSenderUUID());
        activeSessions.remove(session.getTargetUUID());
    }

    /** Возвращает предметы из зон обратно их владельцам. */
    private void returnItemsToOwners(TradeSession session) {
        Player sender = Bukkit.getPlayer(session.getSenderUUID());
        Player target = Bukkit.getPlayer(session.getTargetUUID());
        Inventory inv = session.getInventory();

        if (inv == null) return;

        // Возвращаем предметы отправителю (зона 9-17)
        if (sender != null) {
            for (int i = SENDER_ZONE_START; i <= SENDER_ZONE_END; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR && !TradeSession.isPlaceholder(item)) {
                    HashMap<Integer, ItemStack> leftover = sender.getInventory().addItem(item.clone());
                    for (ItemStack drop : leftover.values()) {
                        sender.getWorld().dropItemNaturally(sender.getLocation(), drop);
                    }
                }
            }
        }

        // Возвращаем предметы получателю (зона 27-35)
        if (target != null) {
            for (int i = TARGET_ZONE_START; i <= TARGET_ZONE_END; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR && !TradeSession.isPlaceholder(item)) {
                    HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item.clone());
                    for (ItemStack drop : leftover.values()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), drop);
                    }
                }
            }
        }

        inv.clear();
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

    // ═══════════════════════════════════════════
    //  ВСПОМОГАТЕЛЬНЫЕ
    // ═══════════════════════════════════════════

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

    private String formatMoney(double amount) {
        Economy eco = OfficePlugin.getEconomy();
        return eco != null ? eco.format(amount) : String.format("$%.2f", amount);
    }

    // ═══════════════════════════════════════════
    //  УТИЛИТЫ ДЛЯ ПРЕДМЕТОВ
    // ═══════════════════════════════════════════

    /** Заполняет целый ряд одним материалом. */
    private void fillRow(Inventory inv, int row, Material mat) {
        int start = row * 9;
        for (int i = start; i < start + 9; i++) {
            inv.setItem(i, makeItem(mat, " "));
        }
    }

    /** Создаёт placeholder-предмет с скрытым тегом в лоре. */
    public static ItemStack makePlaceholder(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.RESET + name);
        List<String> lore = new ArrayList<>();
        lore.add(TradeSession.PLACEHOLDER_TAG);  // скрытый тег
        lore.add(ChatColor.GRAY + "Положите предмет сюда");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Создаёт ItemStack с именем и лором. */
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

    /** Сохраняет реальные предметы из зоны перед очисткой. */
    private void saveItemsFromZone(Inventory inv, int start, int end, List<SavedItem> list) {
        for (int i = start; i <= end; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && !TradeSession.isPlaceholder(item)) {
                list.add(new SavedItem(i, item.clone()));
            }
        }
    }

    /** Вспомогательный класс для хранения пары (слот, предмет). */
    private static class SavedItem {
        final int slot;
        final ItemStack item;
        SavedItem(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }
    }
}

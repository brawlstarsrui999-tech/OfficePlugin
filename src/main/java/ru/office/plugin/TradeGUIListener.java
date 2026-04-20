package ru.office.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Обрабатывает все клики в GUI трейда.
 *
 * Ключевые правила:
 * ─ Каждый игрок может класть/забирать предметы ТОЛЬКО из своей зоны.
 * ─ Отправитель: слоты 9-17 (верхняя зона, голубые placeholder'ы).
 * ─ Получатель:  слоты 27-35 (нижняя зона, розовые placeholder'ы).
 * ─ Нельзя забирать предметы оппонента.
 * ─ Нельзя забирать декоративные элементы (стекло, кнопки и т.д.).
 * ─ Изменение предметов/деней сбрасывает оба подтверждения.
 */
public class TradeGUIListener implements Listener {

    private final OfficePlugin plugin;

    public TradeGUIListener(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isTradeGUI(Inventory inv) {
        if (inv == null || inv.getSize() != TradeManager.GUI_SIZE) return false;
        if (inv.getViewers().isEmpty()) return false;
        String title = inv.getViewers().get(0).getOpenInventory().getTitle();
        return title != null && title.equals(TradeManager.GUI_TITLE);
    }

    // ═══════════════════════════════════════════
    //  КЛИК
    // ═══════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        TradeSession session = plugin.getTradeManager().getSession(player.getUniqueId());
        if (session == null) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isTradeGUI(topInv)) return;

        int rawSlot = event.getRawSlot();
        int topSize = topInv.getSize(); // 54

        // ──────────────────────────────────────
        //  Клик в нижнем инвентаре (инвентарь игрока)
        // ──────────────────────────────────────
        if (rawSlot >= topSize) {
            // Shift-клик: перемещаем предмет в зону трейда
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (session.getState() != TradeSession.State.OPEN) {
                    event.setCancelled(true);
                    return;
                }

                ItemStack moving = event.getCurrentItem();
                if (moving == null || moving.getType() == Material.AIR) {
                    event.setCancelled(true);
                    return;
                }

                boolean isSender = player.getUniqueId().equals(session.getSenderUUID());
                int zoneStart = isSender ? TradeManager.SENDER_ZONE_START : TradeManager.TARGET_ZONE_START;
                int zoneEnd   = isSender ? TradeManager.SENDER_ZONE_END   : TradeManager.TARGET_ZONE_END;

                // Ищем свободный слот в своей зоне
                int freeSlot = -1;
                for (int i = zoneStart; i <= zoneEnd; i++) {
                    ItemStack existing = topInv.getItem(i);
                    if (existing == null || existing.getType() == Material.AIR
                            || TradeSession.isPlaceholder(existing)) {
                        freeSlot = i;
                        break;
                    }
                }

                if (freeSlot == -1) {
                    event.setCancelled(true);
                    player.sendMessage(C.prefix + C.RED + "Ваша зона трейда заполнена! Максимум 9 предметов.");
                    return;
                }

                event.setCancelled(true);
                topInv.setItem(freeSlot, moving.clone());
                event.setCurrentItem(null);

                session.resetConfirmations();
                plugin.getTradeManager().refreshGUI(session, topInv);
                return;
            }
            // Любой другой клик в инвентаре игрока — разрешаем
            return;
        }

        // ──────────────────────────────────────
        //  Клик в верхнем GUI — всё отменяем по умолчанию
        // ──────────────────────────────────────
        event.setCancelled(true);

        // ── Кнопка «Подтвердить» ──
        if (rawSlot == TradeManager.CONFIRM_BUTTON) {
            plugin.getTradeManager().handleConfirmClick(player, session);
            return;
        }

        // ── Кнопка «Отмена» ──
        if (rawSlot == TradeManager.CANCEL_BUTTON) {
            plugin.getTradeManager().cancelSession(session, player.getName() + " отменил трейд.");
            return;
        }

        // ── Кнопки денег отправителя (только отправитель может нажимать) ──
        if (rawSlot == TradeManager.SENDER_MONEY_MINUS
                || rawSlot == TradeManager.SENDER_MONEY_PLUS
                || rawSlot == TradeManager.SENDER_MONEY_DISPLAY) {
            if (player.getUniqueId().equals(session.getSenderUUID())) {
                plugin.getTradeManager().handleMoneyClick(session, true, rawSlot, event.getClick());
            } else {
                player.sendMessage(C.prefix + C.RED + "Это не ваша зона денег!");
            }
            return;
        }

        // ── Кнопки денег получателя (только получатель может нажимать) ──
        if (rawSlot == TradeManager.TARGET_MONEY_MINUS
                || rawSlot == TradeManager.TARGET_MONEY_PLUS
                || rawSlot == TradeManager.TARGET_MONEY_DISPLAY) {
            if (player.getUniqueId().equals(session.getTargetUUID())) {
                plugin.getTradeManager().handleMoneyClick(session, false, rawSlot, event.getClick());
            } else {
                player.sendMessage(C.prefix + C.RED + "Это не ваша зона денег!");
            }
            return;
        }

        // ── Зона предметов отправителя (слоты 9-17) ──
        if (session.isSenderSlot(rawSlot)) {
            // Только отправитель может взаимодействовать
            if (!player.getUniqueId().equals(session.getSenderUUID())) {
                player.sendMessage(C.prefix + C.RED + "Это зона предметов " + C.AQUA + session.getSenderName() + C.RED + "!");
                return;
            }
            if (session.getState() != TradeSession.State.OPEN) {
                player.sendMessage(C.prefix + C.RED + "Нельзя изменять предметы во время подтверждения!");
                return;
            }
            handleTradeZoneClick(event, player, topInv, rawSlot, session);
            return;
        }

        // ── Зона предметов получателя (слоты 27-35) ──
        if (session.isTargetSlot(rawSlot)) {
            // Только получатель может взаимодействовать
            if (!player.getUniqueId().equals(session.getTargetUUID())) {
                player.sendMessage(C.prefix + C.RED + "Это зона предметов " + C.LIGHT_PURPLE + session.getTargetName() + C.RED + "!");
                return;
            }
            if (session.getState() != TradeSession.State.OPEN) {
                player.sendMessage(C.prefix + C.RED + "Нельзя изменять предметы во время подтверждения!");
                return;
            }
            handleTradeZoneClick(event, player, topInv, rawSlot, session);
            return;
        }

        // ── Все остальные слоты (декор, статусы, разделители) — игнорируем ──
    }

    /**
     * Обрабатывает клик в торговой зоне конкретного игрока.
     * Позволяет класть и забирать СВОИ предметы.
     */
    private void handleTradeZoneClick(InventoryClickEvent event, Player player,
                                      Inventory inv, int slot, TradeSession session) {
        ItemStack cursor = event.getCursor();
        ItemStack current = inv.getItem(slot);
        ClickType click = event.getClick();

        // ── Shift-клик: вернуть предмет в инвентарь ──
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            if (current != null && current.getType() != Material.AIR && !TradeSession.isPlaceholder(current)) {
                java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                // Очищаем слот (refreshGUI поставит placeholder)
                inv.setItem(slot, null);
                session.resetConfirmations();
                plugin.getTradeManager().refreshGUI(session, inv);
            }
            return;
        }

        // ── Обычный клик ──
        if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);
            boolean slotHasRealItem = (current != null && current.getType() != Material.AIR
                    && !TradeSession.isPlaceholder(current));

            if (cursorEmpty) {
                // ── Курсор пуст → берём предмет из слота ──
                if (slotHasRealItem) {
                    event.getView().setCursor(current.clone());
                    inv.setItem(slot, null);
                    session.resetConfirmations();
                    plugin.getTradeManager().refreshGUI(session, inv);
                }
            } else {
                // ── На курсоре предмет → кладём в слот ──
                if (current == null || current.getType() == Material.AIR || TradeSession.isPlaceholder(current)) {
                    // Пустой слот / placeholder → кладём
                    inv.setItem(slot, cursor.clone());
                    event.getView().setCursor(null);
                } else if (slotHasRealItem) {
                    // Занятый слот → swap
                    ItemStack tmp = current.clone();
                    inv.setItem(slot, cursor.clone());
                    event.getView().setCursor(tmp);
                }
                session.resetConfirmations();
                plugin.getTradeManager().refreshGUI(session, inv);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  ПЕРЕТАСКИВАНИЕ
    // ═══════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        TradeSession session = plugin.getTradeManager().getSession(event.getWhoClicked().getUniqueId());
        if (session == null) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isTradeGUI(topInv)) return;

        // Полностью запрещаем drag в GUI трейда
        event.setCancelled(true);
    }

    // ═══════════════════════════════════════════
    //  ЗАКРЫТИЕ ИНВЕНТАРЯ
    // ═══════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        TradeSession session = plugin.getTradeManager().getSession(player.getUniqueId());
        if (session == null) return;

        if (!isTradeGUI(event.getInventory())) return;

        TradeSession.State state = session.getState();
        if (state == TradeSession.State.COMPLETED || state == TradeSession.State.CANCELLED) return;

        // Задержка в 1 тик чтобы избежать конфликтов с другими обработчиками
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.getState() == TradeSession.State.OPEN || session.getState() == TradeSession.State.CONFIRMING) {
                plugin.getTradeManager().cancelSession(session, player.getName() + " закрыл инвентарь.");
            }
        });
    }
}

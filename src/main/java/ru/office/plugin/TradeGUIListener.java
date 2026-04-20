package ru.office.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

public class TradeGUIListener implements Listener {

    private static final String GUI_TITLE_MARKER = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ OFFICE TRADE ✦";

    private final OfficePlugin plugin;

    public TradeGUIListener(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isTradeGUI(Inventory inv) {
        if (inv == null) return false;
        // Проверяем по названию и размеру
        String title = inv.getViewers().isEmpty() ? null
                : inv.getViewers().get(0).getOpenInventory().getTitle();
        return title != null && title.equals(GUI_TITLE_MARKER) && inv.getSize() == 36;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        TradeSession session = plugin.getTradeManager().getSession(player.getUniqueId());
        if (session == null) return;

        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        // Убеждаемся, что это наш GUI
        if (!isTradeGUI(topInv)) return;

        int rawSlot = event.getRawSlot();
        int size = topInv.getSize(); // 36

        // Клик в нижнем инвентаре (инвентарь игрока)
        if (rawSlot >= size) {
            // Shift-клик из инвентаря игрока -> попытка перенести предмет в торговую зону
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Разрешаем только если сессия OPEN
                if (session.getState() != TradeSession.State.OPEN) {
                    event.setCancelled(true);
                    return;
                }
                // Ищем свободный слот в зоне трейда (9-17)
                ItemStack moving = event.getCurrentItem();
                if (moving == null || moving.getType() == Material.AIR) {
                    event.setCancelled(true);
                    return;
                }
                int freeSlot = -1;
                for (int i = 9; i <= 17; i++) {
                    ItemStack existing = topInv.getItem(i);
                    if (existing == null || existing.getType() == Material.AIR
                            || existing.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                        freeSlot = i;
                        break;
                    }
                }
                if (freeSlot == -1) {
                    event.setCancelled(true);
                    player.sendMessage(C.prefix + C.RED + "Зона трейда заполнена! Максимум 9 предметов.");
                    return;
                }
                event.setCancelled(true);
                topInv.setItem(freeSlot, moving.clone());
                player.getInventory().remove(moving);
                refreshAfterChange(session);
                return;
            }
            // Любой другой клик в инвентаре игрока — разрешаем
            return;
        }

        // Клик в верхнем GUI
        event.setCancelled(true);

        // Кнопки
        if (rawSlot == 27) {
            plugin.getTradeManager().handleButtonClick(player, session, 27);
            return;
        }
        if (rawSlot == 35) {
            plugin.getTradeManager().handleButtonClick(player, session, 35);
            return;
        }

        // Декорные слоты — ничего не делаем
        if (plugin.getTradeManager().isDecorSlot(rawSlot)) return;

        // Торговая зона (слоты 9-17)
        if (session.getState() != TradeSession.State.OPEN) {
            player.sendMessage(C.prefix + C.RED + "Нельзя изменять предметы во время подтверждения!");
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = topInv.getItem(rawSlot);

        ClickType click = event.getClick();

        // Убираем предмет из слота (берём в руку)
        if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            if (cursor == null || cursor.getType() == Material.AIR) {
                // Берём предмет из слота
                if (current != null && current.getType() != Material.AIR
                        && current.getType() != Material.YELLOW_STAINED_GLASS_PANE) {
                    event.getView().setCursor(current.clone());
                    topInv.setItem(rawSlot, null);
                    refreshAfterChange(session);
                }
            } else {
                // Кладём предмет в слот
                if (current == null || current.getType() == Material.AIR
                        || current.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
                    topInv.setItem(rawSlot, cursor.clone());
                    event.getView().setCursor(null);
                    refreshAfterChange(session);
                } else {
                    // Swap
                    ItemStack tmp = current.clone();
                    topInv.setItem(rawSlot, cursor.clone());
                    event.getView().setCursor(tmp);
                    refreshAfterChange(session);
                }
            }
        } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            // Shift-клик в торговой зоне -> возврат в инвентарь
            if (current != null && current.getType() != Material.AIR
                    && current.getType() != Material.YELLOW_STAINED_GLASS_PANE) {
                topInv.setItem(rawSlot, null);
                // Отдаём игроку
                player.getInventory().addItem(current.clone());
                refreshAfterChange(session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        TradeSession session = plugin.getTradeManager().getSession(player.getUniqueId());
        if (session == null) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isTradeGUI(topInv)) return;

        // Проверяем, не попал ли drag в декорные слоты
        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                // Слоты GUI
                if (plugin.getTradeManager().isDecorSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
                if (session.getState() != TradeSession.State.OPEN) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        TradeSession session = plugin.getTradeManager().getSession(player.getUniqueId());
        if (session == null) return;

        Inventory inv = event.getInventory();
        if (!isTradeGUI(inv)) return;

        // Если трейд уже завершён или отменён — ничего не делаем
        TradeSession.State state = session.getState();
        if (state == TradeSession.State.COMPLETED || state == TradeSession.State.CANCELLED) return;

        // Иначе — отмена трейда при закрытии
        plugin.getTradeManager().cancelSession(session, player.getName() + " закрыл GUI трейда.");
    }

    // ──────────────────────────────────────────────
    //  REFRESH после изменений в торговой зоне
    // ──────────────────────────────────────────────

    private void refreshAfterChange(TradeSession session) {
        Inventory inv = session.getInventory();
        if (inv == null) return;

        // Заменяем пустые торговые слоты на placeholder
        ItemStack placeholder = TradeManager.makeItem(Material.YELLOW_STAINED_GLASS_PANE,
                C.YELLOW + "" + ChatColor.BOLD + "◆ Слот для предметов ◆",
                C.GRAY + "Положите сюда предметы для обмена");

        for (int i = 9; i <= 17; i++) {
            ItemStack current = inv.getItem(i);
            if (current == null || current.getType() == Material.AIR) {
                inv.setItem(i, placeholder);
            }
        }
    }
}

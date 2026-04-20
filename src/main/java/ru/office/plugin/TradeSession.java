package ru.office.plugin;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Активная сессия трейда между двумя игроками.
 *
 * GUI (54 слота, 6 рядов):
 * ┌─────────────────────────────────────────────────┐
 * │ Ряд 0 (0-8):   Декоративная верхняя рамка       │
 * │ Ряд 1 (9-17):  Предметы ОТПРАВИТЕЛЯ (9 слотов)  │
 * │ Ряд 2 (18-26): Деньги +/- и разделитель         │
 * │ Ряд 3 (27-35): Предметы ПОЛУЧАТЕЛЯ (9 слотов)   │
 * │ Ряд 4 (36-44): Статусы подтверждения             │
 * │ Ряд 5 (45-53): Кнопки Подтвердить / Отмена       │
 * └─────────────────────────────────────────────────┘
 */
public class TradeSession {

    public enum State {
        OPEN,           // GUI открыто, идёт наполнение
        CONFIRMING,     // Оба нажали «принять», идёт отсчёт 5 сек
        COMPLETED,      // Трейд завершён
        CANCELLED       // Трейд отменён
    }

    // ─── Зоны предметов ───
    public static final int SENDER_ZONE_START = 9;
    public static final int SENDER_ZONE_END   = 17;
    public static final int TARGET_ZONE_START = 27;
    public static final int TARGET_ZONE_END   = 35;

    // ─── Скрытый тег для placeholder-предметов ───
    public static final String PLACEHOLDER_TAG = "§0TRADE_SLOT_PLACEHOLDER";

    private final UUID senderUUID;
    private final String senderName;
    private final UUID targetUUID;
    private final String targetName;

    private Inventory inventory;
    private State state = State.OPEN;
    private long confirmStartTime = 0L;

    private boolean senderConfirmed = false;
    private boolean targetConfirmed = false;

    // Деньги, которые каждый игрок предлагает в трейде
    private double senderMoney = 0.0;
    private double targetMoney = 0.0;

    private int timerTaskId = -1;

    public TradeSession(UUID senderUUID, String senderName,
                        UUID targetUUID, String targetName) {
        this.senderUUID  = senderUUID;
        this.senderName  = senderName;
        this.targetUUID  = targetUUID;
        this.targetName  = targetName;
    }

    // ──────── Геттеры / Сеттеры ────────

    public UUID   getSenderUUID()  { return senderUUID;  }
    public String getSenderName()  { return senderName;   }
    public UUID   getTargetUUID()  { return targetUUID;   }
    public String getTargetName()  { return targetName;   }

    public Inventory getInventory()              { return inventory; }
    public void      setInventory(Inventory inv) { this.inventory = inv; }

    public State getState()          { return state; }
    public void  setState(State s)   { this.state = s; }

    public long getConfirmStartTime()           { return confirmStartTime; }
    public void setConfirmStartTime(long t)     { this.confirmStartTime = t; }

    public boolean isSenderConfirmed()           { return senderConfirmed; }
    public boolean isTargetConfirmed()           { return targetConfirmed; }
    public void setSenderConfirmed(boolean b)    { this.senderConfirmed = b; }
    public void setTargetConfirmed(boolean b)    { this.targetConfirmed = b; }

    public double getSenderMoney()               { return senderMoney; }
    public void   setSenderMoney(double m)       { this.senderMoney = Math.max(0, m); }
    public double getTargetMoney()               { return targetMoney; }
    public void   setTargetMoney(double m)       { this.targetMoney = Math.max(0, m); }

    public int  getTimerTaskId()             { return timerTaskId; }
    public void setTimerTaskId(int id)       { this.timerTaskId = id; }

    public boolean bothConfirmed() {
        return senderConfirmed && targetConfirmed;
    }

    /** Сбросить оба подтверждения (при изменении предметов / денег). */
    public void resetConfirmations() {
        senderConfirmed = false;
        targetConfirmed = false;
    }

    // ──────── Проверки слотов ────────

    public boolean isSenderSlot(int slot) {
        return slot >= SENDER_ZONE_START && slot <= SENDER_ZONE_END;
    }

    public boolean isTargetSlot(int slot) {
        return slot >= TARGET_ZONE_START && slot <= TARGET_ZONE_END;
    }

    public boolean isTradeSlot(int slot) {
        return isSenderSlot(slot) || isTargetSlot(slot);
    }

    // ──────── Placeholder detection ────────

    /**
     * Проверяет, является ли ItemStack placeholder-слотом в GUI.
     * Placeholder-предметы содержат скрытый тег в лоре.
     */
    public static boolean isPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        return lore != null && lore.contains(PLACEHOLDER_TAG);
    }

    // ──────── Получение предметов ────────

    /** Возвращает реальные предметы из зоны отправителя (без placeholder'ов). */
    public List<ItemStack> getSenderItems() {
        return getItemsFromZone(SENDER_ZONE_START, SENDER_ZONE_END);
    }

    /** Возвращает реальные предметы из зоны получателя (без placeholder'ов). */
    public List<ItemStack> getTargetItems() {
        return getItemsFromZone(TARGET_ZONE_START, TARGET_ZONE_END);
    }

    private List<ItemStack> getItemsFromZone(int start, int end) {
        List<ItemStack> items = new ArrayList<>();
        if (inventory == null) return items;
        for (int i = start; i <= end; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR && !isPlaceholder(item)) {
                items.add(item.clone());
            }
        }
        return items;
    }
}

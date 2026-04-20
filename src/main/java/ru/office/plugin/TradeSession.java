package ru.office.plugin;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Активная сессия трейда между двумя игроками.
 */
public class TradeSession {

    public enum State {
        OPEN,           // GUI открыто, идёт наполнение
        CONFIRMING,     // Оба нажали "принять", идёт отсчёт 5 сек
        COMPLETED,      // Трейд завершён
        CANCELLED       // Трейд отменён
    }

    private final UUID senderUUID;
    private final String senderName;
    private final UUID targetUUID;
    private final String targetName;

    /** Одна shared-инвентарь GUI, открытый для обоих игроков. */
    private Inventory inventory;

    private State state = State.OPEN;
    private long confirmStartTime = 0L;

    // Флаги подтверждения каждого игрока
    private boolean senderConfirmed = false;
    private boolean targetConfirmed = false;

    // ID задачи Bukkit (таймер 5 сек)
    private int timerTaskId = -1;

    public TradeSession(UUID senderUUID, String senderName,
                        UUID targetUUID, String targetName) {
        this.senderUUID = senderUUID;
        this.senderName = senderName;
        this.targetUUID = targetUUID;
        this.targetName = targetName;
    }

    // ---- геттеры / сеттеры ----

    public UUID getSenderUUID()  { return senderUUID; }
    public String getSenderName(){ return senderName;  }
    public UUID getTargetUUID()  { return targetUUID;  }
    public String getTargetName(){ return targetName;  }

    public Inventory getInventory()            { return inventory; }
    public void setInventory(Inventory inv)    { this.inventory = inv; }

    public State getState()                    { return state; }
    public void setState(State s)              { this.state = s; }

    public long getConfirmStartTime()          { return confirmStartTime; }
    public void setConfirmStartTime(long t)    { this.confirmStartTime = t; }

    public boolean isSenderConfirmed()         { return senderConfirmed; }
    public boolean isTargetConfirmed()         { return targetConfirmed; }
    public void setSenderConfirmed(boolean b)  { this.senderConfirmed = b; }
    public void setTargetConfirmed(boolean b)  { this.targetConfirmed = b; }

    public int getTimerTaskId()                { return timerTaskId; }
    public void setTimerTaskId(int id)         { this.timerTaskId = id; }

    public boolean bothConfirmed() {
        return senderConfirmed && targetConfirmed;
    }

    /**
     * Возвращает предметы из "зоны трейда" (слоты 9-17) инвентаря GUI.
     *
     * ИСПРАВЛЕНО: слоты трейда — 9-17 (ряд 1 GUI), а не 0-8 (ряд 0 — декор/рамка).
     * Предыдущий баг: читались слоты 0-8, которые содержат декорные стеклянные панели,
     * поэтому получатель получал стекло вместо реальных предметов игрока.
     */
    public ItemStack[] getTradeItems() {
        if (inventory == null) return new ItemStack[0];
        ItemStack[] all = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(9 + i); // слоты 9-17
            // Дополнительная защита: не возвращать декоративные панели
            if (item != null
                    && item.getType() != Material.AIR
                    && item.getType() != Material.YELLOW_STAINED_GLASS_PANE
                    && item.getType() != Material.GREEN_STAINED_GLASS_PANE
                    && item.getType() != Material.LIME_STAINED_GLASS_PANE
                    && item.getType() != Material.BLACK_STAINED_GLASS_PANE
                    && item.getType() != Material.GRAY_STAINED_GLASS_PANE
                    && item.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                all[i] = item;
            }
        }
        return all;
    }
}

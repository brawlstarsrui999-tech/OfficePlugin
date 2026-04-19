package ru.office.plugin;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Хранит данные о входящем запросе на трейд.
 */
public class TradeRequest {

    private final UUID senderUUID;
    private final String senderName;
    private final UUID targetUUID;
    private final String targetName;
    private final long createdAt;

    /** Стоимость трейда (500$ списывается с отправителя при принятии). */
    public static final double TRADE_COST = 500.0;

    public TradeRequest(Player sender, Player target) {
        this.senderUUID = sender.getUniqueId();
        this.senderName = sender.getName();
        this.targetUUID = target.getUniqueId();
        this.targetName = target.getName();
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getSenderUUID() { return senderUUID; }
    public String getSenderName() { return senderName; }
    public UUID getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public long getCreatedAt() { return createdAt; }

    /** Запрос действителен 60 секунд. */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 60_000L;
    }
}

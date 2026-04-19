package ru.office.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class TradeRequestListener implements Listener {

    private final OfficePlugin plugin;

    public TradeRequestListener(OfficePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var tm = plugin.getTradeManager();

        // Если игрок был в активной сессии — отменяем трейд
        TradeSession session = tm.getSession(player.getUniqueId());
        if (session != null) {
            tm.cancelSession(session, player.getName() + " вышел с сервера.");
        }
    }
}

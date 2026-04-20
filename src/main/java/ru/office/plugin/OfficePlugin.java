package ru.office.plugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class OfficePlugin extends JavaPlugin {

    private static OfficePlugin instance;
    private static Economy economy;
    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault/Economy не найден! Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tradeManager = new TradeManager(this);

        OfficeCommand command = new OfficeCommand(this);
        getCommand("office").setExecutor(command);
        getCommand("office").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new TradeGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeRequestListener(this), this);

        getLogger().info("OfficePlugin успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) {
            tradeManager.cancelAllTrades();
        }
        getLogger().info("OfficePlugin отключён.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static OfficePlugin getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}

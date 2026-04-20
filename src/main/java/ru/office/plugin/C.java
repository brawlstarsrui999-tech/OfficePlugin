package ru.office.plugin;

import org.bukkit.ChatColor;

/**
 * Цветовые константы и утилиты форматирования.
 */
public final class C {

    private C() {}

    public static final String prefix = ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE
            + ChatColor.BOLD + "✦ Office" + ChatColor.DARK_PURPLE + "]" + ChatColor.RESET + " ";

    public static final String BAR = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    // Шорткаты ChatColor
    public static final String RED     = ChatColor.RED.toString();
    public static final String GREEN   = ChatColor.GREEN.toString();
    public static final String YELLOW  = ChatColor.YELLOW.toString();
    public static final String GOLD    = ChatColor.GOLD.toString();
    public static final String AQUA    = ChatColor.AQUA.toString();
    public static final String LIGHT_PURPLE = ChatColor.LIGHT_PURPLE.toString();
    public static final String GRAY    = ChatColor.GRAY.toString();
    public static final String DARK_GRAY = ChatColor.DARK_GRAY.toString();
    public static final String WHITE   = ChatColor.WHITE.toString();
    public static final String BOLD    = ChatColor.BOLD.toString();
    public static final String ITALIC  = ChatColor.ITALIC.toString();
    public static final String RESET   = ChatColor.RESET.toString();
    public static final String DARK_PURPLE = ChatColor.DARK_PURPLE.toString();
}

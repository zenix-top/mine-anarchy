package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Единый форматтер сообщений плагина.
 */
public final class Msg {

    public static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "\u2694 ANARCHY"
            + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private Msg() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(PREFIX + color(msg));
    }

    public static void sendRaw(CommandSender to, String msg) {
        to.sendMessage(color(msg));
    }

    public static void tell(CommandSender to, String msg) {
        to.sendMessage(ChatColor.GRAY + "\u2192 " + color(msg));
    }

    public static void error(CommandSender to, String msg) {
        to.sendMessage(PREFIX + ChatColor.RED + color(msg));
    }

    public static void broadcast(String msg) {
        Bukkit.broadcastMessage(PREFIX + color(msg));
    }
}

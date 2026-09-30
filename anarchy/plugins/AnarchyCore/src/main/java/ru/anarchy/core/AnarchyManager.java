package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Анархия-механики: защита спавна, вечный мир, авто-сохранение,
 * приветствие и таб-лист в стиле анархия-серверов.
 */
public class AnarchyManager implements Listener {

    private final AnarchyCore plugin;
    private final Settings s;
    private final Map<UUID, Location> lastMove = new HashMap<>();

    public AnarchyManager(AnarchyCore plugin) {
        this.plugin = plugin;
        this.s = plugin.getSettings();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        applyWorldSettings();
        startSaveTask();
        plugin.getLogger().info("Анархия-режим: радиус защиты спавна = " + s.spawnRadius +
                (s.spawnProtectionEnabled ? "" : " (заключение отключено — полная анархия)"));
    }

    private void applyWorldSettings() {
        for (World w : Bukkit.getWorlds()) {
            w.setKeepSpawnInMemory(true);
            if (s.worldBorderEnabled) {
                double size = Math.min(s.worldBorderSize, 59999968.0);
                org.bukkit.WorldBorder border = w.getWorldBorder();
                border.setCenter(0, 0);
                border.setSize(size);
                border.setWarningDistance(64);
            }
        }
    }

    /** Анархия = мир живёт вечно: автосейв раз в 10 минут. */
    private void startSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World w : Bukkit.getWorlds()) w.save();
                plugin.getData().save();
            }
        }.runTaskTimer(plugin, 20L * 600, 20L * 600);
    }

    // ---------- Защита спавна ----------

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!s.spawnProtectionEnabled) return;
        Player p = e.getPlayer();
        if (p.hasPermission("anarchy.bypass")) return;
        if (plugin.getUtil().inSpawnProtect(e.getBlock().getLocation())) {
            e.setCancelled(true);
            Msg.tell(p, "&c\u0417\u0434\u0435\u0441\u044c \u0437\u0430\u0449\u0438\u0449\u0435\u043d\u043d\u044b\u0439 \u0441\u043f\u0430\u0432\u043d &8(&7r=" + s.spawnRadius + "&8).");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!s.spawnProtectionEnabled) return;
        Player p = e.getPlayer();
        if (p.hasPermission("anarchy.bypass")) return;
        if (plugin.getUtil().inSpawnProtect(e.getBlock().getLocation())) {
            e.setCancelled(true);
            Msg.tell(p, "&c\u0421\u0442\u0440\u043e\u0438\u0442\u044c \u043d\u0430 \u0441\u043f\u0430\u0432\u043d\u0435 \u0437\u0430\u043f\u0440\u0435\u0449\u0435\u043d\u043e.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void onExplosion(EntityExplodeEvent e) {
        if (!s.spawnProtectionEnabled) return;
        e.blockList().removeIf(b -> plugin.getUtil().inSpawnProtect(b.getLocation()));
    }

    // ---------- Приветствие / tab ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String motd = Msg.color("&7\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c \u043d\u0430 &c&lANARCHY&r&7! \u0417\u0434\u0435\u0441\u044c \u043d\u0435\u0442 \u043f\u0440\u0430\u0432\u0438\u043b, \u043a\u0440\u043e\u043c\u0435 \u043e\u0434\u043d\u043e\u0433\u043e: &c\u0432\u044b\u0436\u0438\u0432\u0430\u0439&7.");
        p.sendMessage("");
        p.sendMessage(motd);
        p.sendMessage(Msg.color("&8" + "-".repeat(46)));
        p.sendMessage(Msg.color("&7\u041c\u0438\u0440 \u0432\u0435\u0447\u0435\u043d &8| &7\u0413\u0440\u0438\u0444 & TNT \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043d\u044b &8| &7\u041f\u043e\u043c\u043e\u0449\u044c: &e/spawn &7\u0438 &e/help"));
        p.sendMessage(Msg.color("&8" + "-".repeat(46)));
        p.setPlayerListHeaderFooter(
                Msg.color("&c&l\u2694 ANARCHY &7| Paper 1.16.5"),
                Msg.color("&7\u0418\u0433\u0440\u043e\u043a\u043e\u0432: &e" + Bukkit.getOnlinePlayers().size() + " &7| TPS: &a" +
                        String.format("%.1f", Bukkit.getTPS()[0]) + "\n&7/anarchy \u2014 \u0438\u043d\u0444\u043e \u043e \u0441\u0435\u0440\u0432\u0435\u0440\u0435"));
        if (s.announceSpawnPvp && plugin.getPve() != null) {
            p.sendMessage(Msg.color("&8[\u041f\u0432\u0415] &7\u0420\u0435\u0433\u0435\u043d \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437 &e" +
                    s.regenDelaySeconds + "&7\u0441 \u043f\u043e\u0441\u043b\u0435\u0434\u043d\u0435\u0433\u043e \u0443\u0440\u043e\u043d\u0430."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        lastMove.put(e.getPlayer().getUniqueId(), to.clone());
    }

    public Location lastKnown(UUID id) {
        return lastMove.get(id);
    }
}

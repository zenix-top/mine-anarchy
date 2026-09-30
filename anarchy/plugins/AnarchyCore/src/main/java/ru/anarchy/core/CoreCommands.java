package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Команды анархия-сервера: спавн/дом/тп, буры, сундуки, инфа.
 */
public class CoreCommands implements TabExecutor {

    private final AnarchyCore plugin;
    private final Settings s;

    public CoreCommands(AnarchyCore plugin) {
        this.plugin = plugin;
        this.s = plugin.getSettings();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "spawn": return spawn(sender);
            case "home": return home(sender, args);
            case "sethome": return setHome(sender, args);
            case "delhome": return delHome(sender, args);
            case "back": return back(sender);
            case "tpdeath": case "deathloc": return tpDeath(sender);
            case "coords": case "xyz": return coords(sender);
            case "drill": return drill(sender, args);
            case "chestinfo": return chestInfo(sender, args);
            case "anarchy": return anarchy(sender);
            case "pve": return pveStatus(sender);
            default: return false;
        }
    }

    private boolean needPlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        Msg.error(sender, "&cКоманда доступна только игрокам.");
        return false;
    }

    // ---------- /spawn ----------
    private boolean spawn(CommandSender sender) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        World w = Bukkit.getWorlds().get(0);
        Location loc = w.getSpawnLocation();
        loc.setY(w.getHighestBlockYAt(loc) + 1.0);
        plugin.getData().setPrevious(p.getUniqueId(), p.getLocation());
        p.teleport(loc);
        Msg.send(p, "&aТелепорт на спавн!");
        return true;
    }

    // ---------- /home [name] | /home set <name> | /home del <name> ----------
    private boolean home(CommandSender sender, String[] args) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        if (args.length == 0) {
            Map<String, Location> homes = plugin.getData().homes(p.getUniqueId());
            if (homes.isEmpty()) {
                Msg.tell(p, "&7У тебя нет домов. &e/sethome <имя>");
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (String h : homes.keySet()) sb.append("&a").append(h).append("&7, ");
            Msg.tell(p, "&7Дома: " + sb);
            return true;
        }
        Location target = plugin.getData().getHome(p.getUniqueId(), args[0]);
        if (target == null) {
            Msg.error(p, "&cДом '" + args[0] + "' не найден.");
            return true;
        }
        plugin.getData().setPrevious(p.getUniqueId(), p.getLocation());
        p.teleport(target);
        Msg.send(p, "&aТелепорт домой &e" + args[0]);
        return true;
    }

    private boolean setHome(CommandSender sender, String[] args) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        String name = args.length > 0 ? args[0].toLowerCase() : "default";
        if (name.length() > 24) {
            Msg.error(p, "&cСлишком длинное имя дома.");
            return true;
        }
        plugin.getData().setHome(p.getUniqueId(), name, p.getLocation());
        plugin.getData().save();
        Msg.send(p, "&aДом &e" + name + " &aустановлен: &7" + AnarchyUtil.fmt(p.getLocation()));
        return true;
    }

    private boolean delHome(CommandSender sender, String[] args) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        if (args.length == 0) {
            Msg.error(p, "&cИспользование: /delhome <имя>");
            return true;
        }
        if (plugin.getData().delHome(p.getUniqueId(), args[0].toLowerCase())) {
            plugin.getData().save();
            Msg.send(p, "&aДом удалён.");
        } else {
            Msg.error(p, "&cТакого дома нет.");
        }
        return true;
    }

    // ---------- /back ----------
    private boolean back(CommandSender sender) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        Location prev = plugin.getData().getPrevious(p.getUniqueId());
        if (prev == null) {
            Msg.error(p, "&cНечего возвращать.");
            return true;
        }
        plugin.getData().setPrevious(p.getUniqueId(), p.getLocation());
        p.teleport(prev);
        Msg.send(p, "&aВозврат: &7" + AnarchyUtil.fmt(prev));
        return true;
    }

    // ---------- /tpdeath ----------
    private boolean tpDeath(CommandSender sender) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        Location d = plugin.getData().getLastDeath(p.getUniqueId());
        if (d == null) {
            Msg.error(p, "&cТы ещё не умирал(а) на этом сервере.");
            return true;
        }
        plugin.getData().setPrevious(p.getUniqueId(), p.getLocation());
        p.teleport(d);
        Msg.send(p, "&aТы на месте последней смерти &7(" + AnarchyUtil.fmt(d) + ")");
        return true;
    }

    // ---------- /coords ----------
    private boolean coords(CommandSender sender) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        Location l = p.getLocation();
        Msg.send(p, "&7Координаты: &e" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                + " &7(" + l.getWorld().getName() + ")"
                + (plugin.getUtil().inSpawnProtect(l) ? " &c[\u0421\u041f\u0410\u0412\u041d]" : ""));
        return true;
    }

    // ---------- /drill give <tier> | /drill mode <0-2> ----------
    private boolean drill(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("anarchy.admin")) {
                Msg.error(sender, "&cНедостаточно прав.");
                return true;
            }
            // /drill give [игрок] <уровень>
            int tier;
            String playerName;
            if (args.length >= 3) {
                playerName = args[1];
                tier = parseInt(args[2], 1);
            } else if (args.length == 2) {
                if (!(sender instanceof Player)) {
                    Msg.error(sender, "&c/drill give <игрок> <1|2|3>");
                    return true;
                }
                playerName = sender.getName();
                tier = parseInt(args[1], 1);
            } else {
                Msg.error(sender, "&c/drill give [игрок] <1|2|3>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(playerName);
            if (target == null) {
                Msg.error(sender, "&cИгрок не в сети.");
                return true;
            }
            plugin.getDrills().give(target, Math.max(1, Math.min(3, tier)));
            if (!(sender instanceof Player) || !target.getUniqueId().equals(((Player) sender).getUniqueId())) {
                Msg.send(sender, "&aБур выдан игроку &e" + target.getName());
            }
            return true;
        }
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        if (args.length >= 2 && args[0].equalsIgnoreCase("mode")) {
            org.bukkit.inventory.ItemStack it = p.getInventory().getItemInMainHand();
            if (!plugin.getDrills().isDrill(it)) {
                Msg.error(p, "&cВозьми бур в руку.");
                return true;
            }
            plugin.getDrills().setMode(it, parseInt(args[1], 0));
            Msg.send(p, "&aРежим бура изменён.");
            return true;
        }
        Msg.sendRaw(p, ChatColor.GRAY + "/drill mode <0|1|2> " + ChatColor.DARK_GRAY + "- " +
                ChatColor.GRAY + "0=\u0441\u0442\u043e\u043b\u0431, 1=\u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0430 3x3, 2=\u0442\u043e\u0447\u043a\u0430");
        if (sender.hasPermission("anarchy.admin")) {
            Msg.sendRaw(p, ChatColor.GRAY + "/drill give <1|2|3> " + ChatColor.DARK_GRAY + "- " +
                    ChatColor.GRAY + "\u0432\u044b\u0434\u0430\u0442\u044c \u0441\u0435\u0431\u0435 \u0431\u0443\u0440");
        }
        return true;
    }

    // ---------- /chestinfo ----------
    private boolean chestInfo(CommandSender sender, String[] args) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        org.bukkit.block.Block target = p.getTargetBlock(null, 8);
        if (target == null || (target.getType() != Material.CHEST && target.getType() != Material.TRAPPED_CHEST)) {
            Msg.error(p, "&cПосмотрите на сундук (до 8 блоков).");
            return true;
        }
        int items = plugin.getChests().countItems(target.getLocation());
        java.util.UUID owner = plugin.getData().getChestOwner(target.getLocation());
        Msg.send(p, "&7\u0421\u0443\u043d\u0434\u0443\u043a: &e" + items + " &7\u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432, \u0441\u0442\u0440\u0430\u043d\u0438\u0446: &e" + s.chestPages);
        if (owner != null) {
            String name = Bukkit.getOfflinePlayer(owner).getName();
            Msg.tell(p, "&7\u041f\u043e\u0441\u0442\u0430\u0432\u0438\u043b: &a" + (name == null ? "?" : name));
        }
        return true;
    }

    // ---------- /anarchy ----------
    private boolean anarchy(CommandSender sender) {
        Msg.sendRaw(sender, "");
        Msg.sendRaw(sender, ChatColor.RED + "        \u2694 ANARCHY SERVER \u2694");
        Msg.sendRaw(sender, ChatColor.DARK_GRAY + "-------------------------------------------");
        Msg.tell(sender, "&7\u0412\u0435\u0440\u0441\u0438\u044f: &ePaper 1.16.5 build 794");
        Msg.tell(sender, "&7\u041c\u0438\u0440: &a\u0432\u0435\u0447\u043d\u044b\u0439&7, \u0433\u0440\u0438\u0444 & TNT &a\u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043d\u044b");
        Msg.tell(sender, "&7\u0417\u0430\u0449\u0438\u0442\u0430 \u0441\u043f\u0430\u0432\u043d\u0430: &e" + (s.spawnProtectionEnabled ? "r=" + s.spawnRadius : "\u0432\u044b\u043a\u043b"));
        Msg.tell(sender, "&7PvE (1.8): &e" + (s.pveEnabled ? "\u0432\u043a\u043b, \u0440\u0435\u0433\u0435\u043d +" + s.regenDelaySeconds + "\u0441" : "\u0432\u044b\u043a\u043b"));
        Msg.tell(sender, "&7\u0411\u0443\u0440\u044b: &e" + (s.drillsEnabled ? "\u0432\u043a\u043b" : "\u0432\u044b\u043a\u043b") +
                " &7| \u0421\u0443\u043d\u0434\u0443\u043a\u0438: &e" + s.chestPages + " \u0441\u0442\u0440.");
        Msg.tell(sender, "&7TPS: &a" + String.format("%.2f", Bukkit.getTPS()[0]) +
                " &7| \u0418\u0433\u0440\u043e\u043a\u043e\u0432: &e" + Bukkit.getOnlinePlayers().size());
        return true;
    }

    // ---------- /pve ----------
    private boolean pveStatus(CommandSender sender) {
        if (!needPlayer(sender)) return true;
        Player p = (Player) sender;
        Msg.send(p, "&7" + plugin.getPve().statusLine(p));
        if (plugin.getPve().isRecentlyDamaged(p)) {
            Msg.tell(p, "&e\u0420\u0435\u0433\u0435\u043d \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d \u043f\u043e\u0441\u043b\u0435 \u0431\u043e\u044f \u2014 \u043f\u0440\u044f\u0447\u0442\u0441\u044f!");
        }
        return true;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase();
        if (cmd.getName().equalsIgnoreCase("drill")) {
            if (args.length == 1) add(out, last, "mode", "give");
            else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) add(out, last, "0", "1", "2");
        } else if (cmd.getName().equalsIgnoreCase("home")) {
            if (args.length == 1 && sender instanceof Player) {
                for (String h : plugin.getData().homes(((Player) sender).getUniqueId()).keySet()) add(out, last, h);
            }
        } else if (cmd.getName().equalsIgnoreCase("sethome") || cmd.getName().equalsIgnoreCase("delhome")) {
            if (args.length == 1 && sender instanceof Player) {
                for (String h : plugin.getData().homes(((Player) sender).getUniqueId()).keySet()) add(out, last, h);
            }
        }
        return out;
    }

    private static void add(List<String> list, String start, String... opts) {
        for (String o : opts) if (o.toLowerCase().startsWith(start)) list.add(o);
    }
}

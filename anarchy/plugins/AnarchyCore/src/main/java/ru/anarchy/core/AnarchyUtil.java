package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Утилиты анархия-сервера: спавн, границы защиты, поиск безопасных мест.
 */
public final class AnarchyUtil {

    private AnarchyCore plugin;

    public AnarchyUtil(AnarchyCore plugin) {
        this.plugin = plugin;
    }

    public Settings settings() {
        return plugin.getSettings();
    }

    /** Центральный спавн мира (или мировое 0,0 если не задан). */
    public Location spawn(World world) {
        if (world == null) return null;
        Location s = world.getSpawnLocation();
        return s == null ? new Location(world, 0, world.getHighestBlockYAt(0, 0), 0) : s;
    }

    /** Находится ли локация в защищённой зоне спавна. */
    public boolean inSpawnProtect(Location loc) {
        if (!settings().spawnProtectionEnabled || loc == null) return false;
        World w = loc.getWorld();
        if (w == null) return false;
        // В аду и конце защита спавна не действует — там полная анархия
        if (w.getEnvironment() != World.Environment.NORMAL) return false;
        Location s = spawn(w);
        double r = settings().spawnRadius;
        double dx = loc.getX() - s.getX();
        double dz = loc.getZ() - s.getZ();
        return dx * dx + dz * dz <= r * r;
    }

    /** Можно ли гриферить (ломать/ставить блоки) на локации. */
    public boolean canGrief(Location loc) {
        if (loc == null) return true;
        if (!inSpawnProtect(loc)) return true;
        return !settings().spawnProtectionEnabled;
    }

    public boolean isNether(World w) {
        return w != null && w.getEnvironment() == World.Environment.NETHER;
    }

    public boolean isEnd(World w) {
        return w != null && w.getEnvironment() == World.Environment.THE_END;
    }

    /** Ищет ближайшую незащищённую точку рядом с локацией (для /tpdeath и т.п.). */
    public Location safeNear(Location loc, int radius) {
        if (loc == null) return null;
        for (int r = 0; r <= radius; r += 8) {
            for (int x = -r; x <= r; x += 16) {
                for (int z = -r; z <= r; z += 16) {
                    Location t = loc.clone().add(x, 0, z);
                    if (!inSpawnProtect(t)) {
                        t.setY(t.getWorld().getHighestBlockYAt(t) + 1.0);
                        return t;
                    }
                }
            }
        }
        return loc;
    }

    public void teleport(Player p, Location to) {
        if (to == null || p == null) return;
        p.teleport(to);
    }

    public static String fmt(Location l) {
        if (l == null || l.getWorld() == null) return "нет";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    public void debug(String s) {
        if (settings().debug()) {
            Bukkit.getLogger().info("[AnarchyCore.debug] " + s);
        }
    }
}

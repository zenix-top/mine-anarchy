package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Хранилище "вечных" данных анархия-сервера:
 *  - точки дома (/home) по мирам
 *  - последние координаты игрока для /back и /tpdeath
 *  - привязка больших сундуков к владельцу
 */
public class DataStore {

    private final AnarchyCore plugin;
    private File file;
    private YamlConfiguration yaml;

    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Location> lastDeath = new HashMap<>();
    private final Map<UUID, Location> previousLoc = new HashMap<>();
    private final Map<String, UUID> chestOwners = new HashMap<>(); // "world;x;y;z" -> owner

    public DataStore(AnarchyCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось создать data.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        homes.clear();
        lastDeath.clear();
        previousLoc.clear();
        chestOwners.clear();

        if (yaml.isConfigurationSection("homes")) {
            for (String uuidStr : yaml.getConfigurationSection("homes").getKeys(false)) {
                UUID uuid = parseUuid(uuidStr);
                if (uuid == null) continue;
                Map<String, Location> map = new HashMap<>();
                for (String home : yaml.getConfigurationSection("homes." + uuidStr).getKeys(false)) {
                    Location loc = yaml.getLocation("homes." + uuidStr + "." + home);
                    if (loc != null) map.put(home.toLowerCase(), loc);
                }
                homes.put(uuid, map);
            }
        }
        if (yaml.isConfigurationSection("last-death")) {
            for (String uuidStr : yaml.getConfigurationSection("last-death").getKeys(false)) {
                UUID uuid = parseUuid(uuidStr);
                Location loc = yaml.getLocation("last-death." + uuidStr);
                if (uuid != null && loc != null) lastDeath.put(uuid, loc);
            }
        }
        if (yaml.isConfigurationSection("previous")) {
            for (String uuidStr : yaml.getConfigurationSection("previous").getKeys(false)) {
                UUID uuid = parseUuid(uuidStr);
                Location loc = yaml.getLocation("previous." + uuidStr);
                if (uuid != null && loc != null) previousLoc.put(uuid, loc);
            }
        }
        if (yaml.isConfigurationSection("chests")) {
            for (String key : yaml.getConfigurationSection("chests").getKeys(false)) {
                String v = yaml.getString("chests." + key);
                if (v == null) continue;
                UUID u = parseUuid(v);
                if (u != null) chestOwners.put(key, u);
            }
        }
        plugin.getLogger().info("DataStore загружен: homes=" + homes.size() + ", chests=" + chestOwners.size());
    }

    public void save() {
        yaml.set("homes", null);
        yaml.set("last-death", null);
        yaml.set("previous", null);
        yaml.set("chests", null);
        for (Map.Entry<UUID, Map<String, Location>> e : homes.entrySet()) {
            for (Map.Entry<String, Location> h : e.getValue().entrySet()) {
                yaml.set("homes." + e.getKey() + "." + h.getKey(), h.getValue());
            }
        }
        for (Map.Entry<UUID, Location> e : lastDeath.entrySet()) {
            yaml.set("last-death." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Location> e : previousLoc.entrySet()) {
            yaml.set("previous." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, UUID> e : chestOwners.entrySet()) {
            yaml.set("chests." + e.getKey(), e.getValue().toString());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить data.yml", ex);
        }
    }

    // ---------- API ----------

    public Location getHome(UUID id, String name) {
        Map<String, Location> m = homes.get(id);
        return m == null ? null : m.get(name.toLowerCase());
    }

    public boolean setHome(UUID id, String name, Location loc) {
        homes.computeIfAbsent(id, k -> new HashMap<>()).put(name.toLowerCase(), loc.clone());
        return true;
    }

    public boolean delHome(UUID id, String name) {
        Map<String, Location> m = homes.get(id);
        return m != null && m.remove(name.toLowerCase()) != null;
    }

    public Map<String, Location> homes(UUID id) {
        return homes.getOrDefault(id, new HashMap<>());
    }

    public Location getLastDeath(UUID id) {
        return lastDeath.get(id);
    }

    public void setLastDeath(UUID id, Location loc) {
        lastDeath.put(id, loc.clone());
    }

    public Location getPrevious(UUID id) {
        return previousLoc.get(id);
    }

    public void setPrevious(UUID id, Location loc) {
        previousLoc.put(id, loc.clone());
    }

    public static String key(Location loc) {
        World w = loc.getWorld();
        return (w == null ? "?" : w.getName()) + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public UUID getChestOwner(Location loc) {
        return chestOwners.get(key(loc));
    }

    public void setChestOwner(Location loc, UUID owner) {
        chestOwners.put(key(loc), owner);
    }

    public void removeChest(Location loc) {
        chestOwners.remove(key(loc));
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

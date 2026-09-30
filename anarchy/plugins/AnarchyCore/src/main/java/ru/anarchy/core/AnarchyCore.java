package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AnarchyCore — ядро профессионального анархия-сервера на Paper 1.16.5.
 *
 * Модули:
 *  - PveManager    : PvE в стиле 1.8 (долгие бои, медленный реген, кристалл-пвп)
 *  - DrillManager  : буры с топливом, режимами и прочностью
 *  - ChestManager  : сундуки на несколько страниц
 *  - AnarchyManager: защита спавна, вечный мир, приветствия
 *  - CoreCommands  : /spawn /home /sethome /back /tpdeath /drill /anarchy ...
 */
public final class AnarchyCore extends JavaPlugin {

    private static AnarchyCore instance;

    private Settings settings;
    private DataStore data;
    private AnarchyUtil util;
    private PveManager pve;
    private DrillManager drills;
    private ChestManager chests;
    private AnarchyManager anarchy;

    @Override
    public void onEnable() {
        instance = this;
        long t0 = System.currentTimeMillis();

        settings = new Settings(this);
        settings.load();

        data = new DataStore(this);
        data.load();

        util = new AnarchyUtil(this);

        pve = new PveManager(this);
        pve.register();

        drills = new DrillManager(this);
        drills.register();

        chests = new ChestManager(this);
        chests.register();

        anarchy = new AnarchyManager(this);
        anarchy.register();

        registerCommands();

        getLogger().info("AnarchyCore v" + getDescription().getVersion() +
                " включён за " + (System.currentTimeMillis() - t0) + " мс. Good luck, have fun.");
    }

    @Override
    public void onDisable() {
        if (chests != null) chests.save();
        if (data != null) data.save();
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }
        getLogger().info("AnarchyCore выключен. Мир сохранён — он вечный.");
    }

    private void registerCommands() {
        CoreCommands exec = new CoreCommands(this);
        String[] cmds = {"spawn", "home", "sethome", "delhome", "back", "tpdeath",
                "coords", "drill", "chestinfo", "anarchy", "pve"};
        for (String name : cmds) {
            PluginCommand pc = getCommand(name);
            if (pc == null) {
                getLogger().warning("Команда '" + name + "' не найдена в plugin.yml!");
                continue;
            }
            pc.setExecutor(exec);
            pc.setTabCompleter(exec);
        }
    }

    // ---------- Геттеры ----------

    public static AnarchyCore getInstance() {
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public DataStore getData() {
        return data;
    }

    public AnarchyUtil getUtil() {
        return util;
    }

    public PveManager getPve() {
        return pve;
    }

    public DrillManager getDrills() {
        return drills;
    }

    public ChestManager getChests() {
        return chests;
    }

    public AnarchyManager getAnarchy() {
        return anarchy;
    }
}

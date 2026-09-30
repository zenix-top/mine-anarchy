package ru.anarchy.core;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Централизованное хранилище настроек всех модулей.
 * Значения по умолчанию зашиты в code, чтобы конфиг не ломался при удалении секций.
 */
public class Settings {

    private final AnarchyCore plugin;
    private FileConfiguration c;

    // ---- Общие ----
    public boolean announceSpawnPvp;

    // ---- PvE (1.8-стиль) ----
    public boolean pveEnabled;
    public double pveBaseHealth;
    public int regenDelaySeconds;      // сколько секунд после урона реген не работает
    public double potionRegenAmplifier;// сила зелья регена (0 = реген 1, т.е. медленно и долго)
    public boolean disableAbsorption;
    public boolean crystalPvpEnabled;  // кристалл-пвп как на анархия-серверах
    public boolean endCrystalBreaksBlocks;
    public boolean tntDuplication;     // allow old-school TNT duplication mechanics
    public boolean keepInventoryOnDeath;

    // ---- Буры ----
    public boolean drillsEnabled;
    public int drillFuelMax;
    public double drillFuelPerBlock;
    public int drillDurabilityMax;
    public boolean drillRepairWithDiamond;
    public boolean drillLavaSafe;

    // ---- Сундуки (多 страницы) ----
    public boolean bigChestsEnabled;
    public int chestPages;
    public boolean chestRequirePickaxe;
    public boolean chestLoreShowOwner;

    // ---- Анархия / спавн ----
    public boolean spawnProtectionEnabled;
    public int spawnRadius;
    public int spawnWorldMinY;
    public boolean allowGriefOutsideSpawn;
    public boolean netherPortalAnarchy; // отключает лимиты/защиту в аду

    // ---- Терраформинг / производительность ----
    public boolean worldBorderEnabled;
    public double worldBorderSize;
    public boolean clearLagChunks;
    public int maxTntEntities;

    public Settings(AnarchyCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        c = plugin.getConfig();

        announceSpawnPvp = c.getBoolean("general.announce-spawn-pvp", true);

        pveEnabled = c.getBoolean("pve.enabled", true);
        pveBaseHealth = c.getDouble("pve.player-base-health", 20.0);
        regenDelaySeconds = c.getInt("pve.regen-delay-seconds", 60);
        potionRegenAmplifier = c.getDouble("pve.potion-regen-amplifier", 0.0);
        disableAbsorption = c.getBoolean("pve.disable-absorption", true);
        crystalPvpEnabled = c.getBoolean("pve.crystal-pvp", true);
        endCrystalBreaksBlocks = c.getBoolean("pve.end-crystal-breaks-blocks", false);
        tntDuplication = c.getBoolean("pve.tnt-duplication", true);
        keepInventoryOnDeath = c.getBoolean("pve.keep-inventory", false);

        drillsEnabled = c.getBoolean("drills.enabled", true);
        drillFuelMax = c.getInt("drills.fuel-max", 4096);
        drillFuelPerBlock = c.getDouble("drills.fuel-per-block", 1.0);
        drillDurabilityMax = c.getInt("drills.durability-max", 8000);
        drillRepairWithDiamond = c.getBoolean("drills.repair-with-diamond", true);
        drillLavaSafe = c.getBoolean("drills.lava-safe", true);

        bigChestsEnabled = c.getBoolean("big-chests.enabled", true);
        chestPages = c.getInt("big-chests.pages", 6);
        chestRequirePickaxe = c.getBoolean("big-chests.require-pickaxe-to-mine", true);
        chestLoreShowOwner = c.getBoolean("big-chests.show-owner-in-lore", true);

        spawnProtectionEnabled = c.getBoolean("anarchy.spawn-protection", true);
        spawnRadius = c.getInt("anarchy.spawn-radius", 512);
        spawnWorldMinY = c.getInt("anarchy.spawn-world-min-y", -64);
        allowGriefOutsideSpawn = c.getBoolean("anarchy.allow-grief-outside-spawn", true);
        netherPortalAnarchy = c.getBoolean("anarchy.nether-no-limits", true);

        worldBorderEnabled = c.getBoolean("performance.world-border", true);
        worldBorderSize = c.getDouble("performance.world-border-size", 50000000.0);
        clearLagChunks = c.getBoolean("performance.auto-unload-chunks", true);
        maxTntEntities = c.getInt("performance.max-tnt-entities", 600);
    }

    public boolean debug() {
        return c.getBoolean("general.debug", false);
    }
}

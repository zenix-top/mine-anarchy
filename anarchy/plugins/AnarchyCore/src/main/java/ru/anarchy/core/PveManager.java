package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PvE в стиле 1.8: долгие бои, медленная регенерация, кристалл-пвп,
 * отсутствие мгновенного "one-shot" как в новых версиях.
 */
public class PveManager implements Listener {

    private final AnarchyCore plugin;
    private final Settings s;

    /** Когда последний раз был урон (для задержки регена). */
    private final Map<UUID, Long> lastDamaged = new HashMap<>();
    /** Кулдаун установки кристалла, чтобы не спамить. */
    private final Map<UUID, Long> crystalCooldown = new HashMap<>();

    public PveManager(AnarchyCore plugin) {
        this.plugin = plugin;
        this.s = plugin.getSettings();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startRegenTask();
        if (s.crystalPvpEnabled) {
            plugin.getLogger().info("PvE (1.8-style): включён. Долгие бои, кристалл-пвп.");
        }
    }

    // ---------- Настройки игрока при входе ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    public void apply(Player p) {
        if (!s.pveEnabled) return;
        double base = Math.max(6.0, s.pveBaseHealth);
        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(base);
        p.setHealth(Math.min(p.getHealth(), base));
        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0); // 1.8-тайминг атаки
        p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20, 0, true, false), true);
    }

    // ---------- Урон: фиксируем время для отложенного регена ----------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        lastDamaged.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // Ослабляем "криты" и кувыркание, как на 1.8 — удар всегда честный по таймингу
        if (!(e.getEntity() instanceof Player)) return;
        if (!s.pveEnabled) return;
        // Щит не должен полностью обнулять урон от кристаллов (аналогично анархии)
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            e.setDamage(e.getDamage() * 1.15);
        }
    }

    // ---------- Еда / голод ----------

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!s.pveEnabled) return;
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        // Голод падает медленнее, но и еда не даёт мгновенного насыщения хп
        int food = e.getFoodLevel();
        if (food > 6) e.setFoodLevel(Math.max(6, food - 1));
    }

    // ---------- Регенерация с большой задержкой (долгие битвы) ----------

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!s.pveEnabled) return;
                long now = System.currentTimeMillis();
                long delayMs = Math.max(0, s.regenDelaySeconds) * 1000L;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                    Long last = lastDamaged.get(p.getUniqueId());
                    if (last != null && now - last < delayMs) continue;
                    double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    if (p.getHealth() >= max) continue;
                    if (p.getFoodLevel() < 10) continue;
                    // очень медленно: +1 хп каждые 4 секунды (как 1.8, только ещё дольше)
                    p.setHealth(Math.min(max, p.getHealth() + 1.0));
                    if (p.getFoodLevel() > 6) {
                        p.setFoodLevel(p.getFoodLevel() - 1);
                        p.setSaturation(0f);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 4, 20L * 4);
    }

    // ---------- Отключение мгновенной регенерации от зелий/молока? Нет: оставляем, но слабее ----------

    @EventHandler
    public void onJoinCleanAbsorption(PlayerJoinEvent e) {
        if (!s.disableAbsorption) return;
        for (PotionEffect pe : e.getPlayer().getActivePotionEffects()) {
            if (pe.getType().equals(PotionEffectType.ABSORPTION)) {
                e.getPlayer().removePotionEffect(PotionEffectType.ABSORPTION);
            }
        }
    }

    // ---------- Кристалл-пвп: установка кристаллов на блоки obsidian/bedrock ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!s.crystalPvpEnabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.END_CRYSTAL) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Location blockLoc = e.getClickedBlock() == null ? null : e.getClickedBlock().getLocation();
        if (blockLoc == null) return;
        Material bm = e.getClickedBlock().getType();
        if (bm != Material.OBSIDIAN && bm != Material.BEDROCK) return;

        long now = System.currentTimeMillis();
        Long cd = crystalCooldown.get(p.getUniqueId());
        if (cd != null && now - cd < 300) {
            e.setCancelled(true);
            return;
        }
        crystalCooldown.put(p.getUniqueId(), now);

        Location spawnLoc = blockLoc.clone().add(0.5, 1.0, 0.5);
        // Разрешаем ставить кристалл только если там пусто
        for (Entity en : spawnLoc.getWorld().getNearbyEntities(spawnLoc, 0.4, 0.9, 0.4)) {
            if (en instanceof EnderCrystal) {
                e.setCancelled(true);
                return;
            }
        }
        EnderCrystal crystal = spawnLoc.getWorld().spawn(spawnLoc, EnderCrystal.class);
        crystal.setShowingBottom(false);
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
        e.setCancelled(true);
    }

    // ---------- Взрыв кристалла ломает блоки (как на анархии) ----------

    @EventHandler(ignoreCancelled = true)
    public void onCrystalExplode(EntityDamageByEntityEvent e) {
        if (!s.endCrystalBreaksBlocks) return;
        if (!(e.getDamager() instanceof TNTPrimed)) return;
        // заглушка для будущей тонкой настройки
    }

    // ---------- TNT-дуп и защита от переполнения сущностями ----------

    @EventHandler(ignoreCancelled = true)
    public void onTntSpawn(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed)) return;
        int count = 0;
        for (Entity en : e.getLocation().getWorld().getEntitiesByClass(TNTPrimed.class)) {
            count++;
            if (count > s.maxTntEntities) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ---------- Drop protection near spawn (анти-дюп мусора на спавне) ----------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.getUtil().inSpawnProtect(e.getPlayer().getLocation())) return;
        if (e.getItemDrop().getItemStack().getAmount() <= 1) return;
        e.getItemDrop().remove();
        Msg.tell(e.getPlayer(), "&cНа спавне нельзя выбрасывать стакающиеся предметы (анти-лаг).");
    }

    // ---------- Смерть: сохраняем точку для /tpdeath ----------

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        plugin.getData().setLastDeath(p.getUniqueId(), p.getLocation());
        if (s.keepInventoryOnDeath) {
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
    }

    // ---------- Публичный API ----------

    public boolean isRecentlyDamaged(Player p) {
        Long last = lastDamaged.get(p.getUniqueId());
        if (last == null) return false;
        return System.currentTimeMillis() - last < Math.max(0, s.regenDelaySeconds) * 1000L;
    }

    public String statusLine(Player p) {
        return ChatColor.GRAY + "HP: " + ChatColor.RED + String.format("%.1f", p.getHealth())
                + ChatColor.GRAY + "/" + String.format("%.1f", p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())
                + (isRecentlyDamaged(p) ? ChatColor.YELLOW + " (реген заблокирован)" : "");
    }
}

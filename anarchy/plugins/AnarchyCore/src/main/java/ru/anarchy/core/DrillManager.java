package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Буры (drills) — кастомные инструменты для скоростной добычи блоков.
 * Три уровня: железный, алмазный, незеритовый. Требуют топливо (лава/масло),
 * имеют прочность, работают по задержке между блоками, чтобы не лагало.
 */
public class DrillManager implements Listener {

    private final AnarchyCore plugin;
    private final Settings s;

    public static final String TAG_FUEL = "anarchy:drill_fuel";
    public static final String TAG_MODE = "anarchy:drill_mode"; // 0=3x1 вниз, 1=3x3, 2=точка
    public static final String TAG_TIER = "anarchy:drill_tier"; // 1 iron, 2 diamond, 3 netherite

    /** Last block broken per player to avoid double-processing. */
    private final java.util.Set<String> busy = new java.util.HashSet<>();

    public DrillManager(AnarchyCore plugin) {
        this.plugin = plugin;
        this.s = plugin.getSettings();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Буры: активны (" + (s.drillsEnabled ? "вкл" : "выкл") + ")");
    }

    // ---------- Создание предмета бура ----------

    public ItemStack createDrill(int tier) {
        Material mat = tier >= 3 ? Material.NETHERITE_PICKAXE : (tier == 2 ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name;
        List<String> lore = new ArrayList<>();
        switch (tier) {
            case 3:
                name = "&6&lБУР &8[\u2699 \u041d\u0435\u0437\u0435\u0440\u0438\u0442]";
                lore.add("&7Скорость: &a\u26a1\u26a1\u26a1");
                break;
            case 2:
                name = "&b&lБУР &8[\u2699 \u0410\u043b\u043c\u0430\u0437]";
                lore.add("&7Скорость: &a\u26a1\u26a1");
                break;
            default:
                name = "&f&lБУР &8[\u2699 \u0416\u0435\u043b\u0435\u0437\u043e]";
                lore.add("&7Скорость: &a\u26a1");
                break;
        }
        meta.setDisplayName(Msg.color(name));
        lore.add("");
        lore.add("&7Прочность: &e" + s.drillDurabilityMax);
        lore.add("&7Топливо: &c" + s.drillFuelMax + " \u2603");
        lore.add("&7ЛКМ+Shift по блоку &8- &7копать область");
        lore.add("&7ПКМ с &c\u0412\u0435\u0434\u0440\u043e\u043c \u043b\u0430\u0432\u044b &8- &7заправка");
        lore.add("&7ПКМ с &b\u0410\u043b\u043c\u0430\u0437\u043e\u043c &8- &7починка");
        meta.setLore(lore.stream().map(Msg::color).collect(java.util.stream.Collectors.toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        // PDC храним состояние
        var pdc = meta.getPersistentDataContainer();
        pdc.set(new org.bukkit.NamespacedKey(plugin, "drill"), PersistentDataType.BYTE, (byte) 1);
        pdc.set(new org.bukkit.NamespacedKey(plugin, TAG_TIER), PersistentDataType.INTEGER, tier);
        pdc.set(new org.bukkit.NamespacedKey(plugin, TAG_FUEL), PersistentDataType.INTEGER, s.drillFuelMax);
        pdc.set(new org.bukkit.NamespacedKey(plugin, TAG_MODE), PersistentDataType.INTEGER, 0);
        meta.addEnchant(Enchantment.DIG_SPEED, 1 + tier * 3, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDrill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(plugin, "drill"), PersistentDataType.BYTE);
    }

    public int getTier(ItemStack item) {
        if (!isDrill(item)) return 0;
        Integer t = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, TAG_TIER), PersistentDataType.INTEGER);
        return t == null ? 1 : t;
    }

    public int getFuel(ItemStack item) {
        if (!isDrill(item)) return 0;
        Integer f = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, TAG_FUEL), PersistentDataType.INTEGER);
        return f == null ? 0 : f;
    }

    public void setFuel(ItemStack item, int fuel) {
        if (!isDrill(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, TAG_FUEL),
                PersistentDataType.INTEGER, Math.max(0, Math.min(s.drillFuelMax, fuel)));
        updateLore(meta, item.getType());
        item.setItemMeta(meta);
    }

    public int getMode(ItemStack item) {
        if (!isDrill(item)) return 0;
        Integer m = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, TAG_MODE), PersistentDataType.INTEGER);
        return m == null ? 0 : m;
    }

    public void setMode(ItemStack item, int mode) {
        if (!isDrill(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, TAG_MODE),
                PersistentDataType.INTEGER, mode % 3);
        item.setItemMeta(meta);
    }

    private void updateLore(ItemMeta meta, Material type) {
        List<String> lore = meta.getLore();
        if (lore == null) return;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("\u2603")) {
                Integer f = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, TAG_FUEL),
                        PersistentDataType.INTEGER);
                lore.set(i, Msg.color("&7\u0422\u043e\u043f\u043b\u0438\u0432\u043e: &c" + (f == null ? 0 : f)
                        + "/" + s.drillFuelMax + " \u2603"));
            }
        }
        meta.setLore(lore);
    }

    // ---------- Взаимодействие: заправка / починка / смена режима ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!s.drillsEnabled) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack hand = e.getItem();
        org.bukkit.entity.Player p = e.getPlayer();
        ItemStack off = p.getInventory().getItemInOffHand();

        // Заправка буром ведра лавы в руке, бур во второй руке
        if (hand != null && hand.getType() == Material.LAVA_BUCKET && isDrill(off)) {
            refuel(p, off, true);
            e.setCancelled(true);
            return;
        }
        if (isDrill(hand) && off.getType() == Material.LAVA_BUCKET) {
            refuel(p, hand, false);
            e.setCancelled(true);
            return;
        }

        // Починка алмазом
        if (isDrill(hand) && (off.getType() == Material.DIAMOND || off.getType() == Material.NETHERITE_INGOT)) {
            repair(p, hand, off.getType() == Material.NETHERITE_INGOT ? 3 : 1, false);
            e.setCancelled(true);
            return;
        }

        // Смена режима Shift+ПКМ по воздуху
        if (isDrill(hand) && p.isSneaking() && action == Action.RIGHT_CLICK_AIR) {
            int m = (getMode(hand) + 1) % 3;
            setMode(hand, m);
            String[] names = {"\u2b07 \u0421\u0442\u043e\u043b\u0431 3x1", "\u25a3 \u041f\u043b\u043e\u0449\u0430\u0434\u044c 3x3", "\u2022 \u0422\u043e\u0447\u043a\u0430"};
            Msg.tell(p, "&7\u0420\u0435\u0436\u0438\u043c \u0431\u0443\u0440\u0430: &a" + names[m]);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            e.setCancelled(true);
        }
    }

    private void refuel(org.bukkit.entity.Player p, ItemStack drill, boolean mainIsBucket) {
        int cur = getFuel(drill);
        int add = s.drillFuelMax; // полное ведро = полный бак
        int space = s.drillFuelMax - cur;
        if (space <= 0) {
            Msg.tell(p, "&7\u0411\u0443\u0440 \u0443\u0436\u0435 \u043f\u043e\u043b\u043d\u044b\u0439.");
            return;
        }
        setFuel(drill, cur + Math.min(add, space));
        // заменяем ведро лавы на пустое
        ItemStack bucket = new ItemStack(Material.BUCKET);
        if (mainIsBucket) p.getInventory().setItemInMainHand(bucket);
        else p.getInventory().setItemInOffHand(bucket);
        p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 0.8f, 1f);
        Msg.tell(p, "&a\u0411\u0443\u0440 \u0437\u0430\u043f\u0440\u0430\u0432\u043b\u0435\u043d &8(&a+" + Math.min(add, space) + " \u2603&8)");
    }

    /** Текущее "истрепление" бура (единиц из drillDurabilityMax). Хранится в PDC. */
    public int getWear(ItemStack item) {
        if (!isDrill(item)) return 0;
        Integer w = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "wear"), PersistentDataType.INTEGER);
        return w == null ? 0 : w;
    }

    public void setWear(ItemStack item, int wear) {
        if (!isDrill(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "wear"),
                PersistentDataType.INTEGER, Math.max(0, wear));
        updateDurabilityLore(meta);
        item.setItemMeta(meta);
    }

    private void updateDurabilityLore(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) return;
        Integer w = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "wear"),
                PersistentDataType.INTEGER);
        int wear = w == null ? 0 : w;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("\u041f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c:")) { // "Прочность:"
                lore.set(i, Msg.color("&7\u041f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c: &e"
                        + Math.max(0, s.drillDurabilityMax - wear) + "/" + s.drillDurabilityMax));
            }
        }
        meta.setLore(lore);
    }

    private void repair(org.bukkit.entity.Player p, ItemStack drill, int power, boolean offhandUsed) {
        int dur = getWear(drill);
        int max = s.drillDurabilityMax;
        int heal = power == 3 ? max / 4 : max / 10;
        int nd = Math.max(0, dur - heal);
        setWear(drill, nd);
        if (offhandUsed) {
            ItemStack off = p.getInventory().getItemInOffHand();
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1);
            else p.getInventory().setItemInOffHand(null);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
        Msg.tell(p, "&a\u0411\u0443\u0440 \u043f\u043e\u0447\u0438\u043d\u0435\u043d &8(-" + heal + " \u043f\u043e\u0432\u0440\u0435\u0436\u0434\u0435\u043d\u0438\u044f)");
    }

    // ---------- Копание блоков буром ----------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (!s.drillsEnabled) return;
        org.bukkit.entity.Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isDrill(hand)) return;
        if (p.isSneaking()) return; // одиночный блок без траты топлива
        Location loc = e.getBlock().getLocation();
        String key = DataStore.key(loc);
        if (busy.contains(key)) return;

        int mode = getMode(hand);
        int fuel = getFuel(hand);
        List<Location> targets = new ArrayList<>();
        switch (mode) {
            case 0: // столб вниз 3 блока
                for (int dy = 0; dy >= -2; dy--) {
                    targets.add(loc.clone().add(0, dy, 0));
                }
                break;
            case 1: // площадь 3x3 горизонтально
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        targets.add(loc.clone().add(dx, 0, dz));
                break;
            default:
                targets.add(loc);
        }

        int mined = 0;
        for (Location t : targets) {
            if (fuel - (mined + 1) * s.drillFuelPerBlock < 0) break;
            org.bukkit.block.Block b = t.getWorld() == null ? null : t.getWorld().getBlockAt(t);
            if (b == null || b.getType() == Material.AIR || !b.getType().isSolid()) continue;
            if (b.getType() == Material.BEDROCK || b.getType() == Material.BARRIER) continue;
            if (plugin.getUtil().inSpawnProtect(b.getLocation())) continue;
            busy.add(DataStore.key(b.getLocation()));
            boolean ok = b.breakNaturally(hand);
            busy.remove(DataStore.key(b.getLocation()));
            if (ok) mined++;
        }
        if (mined == 0) return;
        setFuel(hand, (int) (fuel - mined * s.drillFuelPerBlock));
        // урон прочности (хранится в PDC, предмет unbreakable)
        int d = getWear(hand) + mined;
        if (d >= s.drillDurabilityMax) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            Msg.error(p, "&c\u0411\u0443\u0440 \u0441\u043b\u043e\u043c\u0430\u043b\u0441\u044f!");
        } else {
            setWear(hand, d);
        }
        p.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc.clone().add(0.5, 0.5, 0.5), 6 + mined, 0.2, 0.2, 0.2, 0.01);
        if (fuel - mined * s.drillFuelPerBlock <= s.drillFuelMax * 0.1) {
            Msg.tell(p, "&c\u26a0 \u0411\u0443\u0440 \u043f\u043e\u0447\u0442\u0438 \u0431\u0435\u0437 \u0442\u043e\u043f\u043b\u0438\u0432\u0430! &8(\u0412\u0435\u0434\u0440\u043e \u043b\u0430\u0432\u044b \u0432 \u0440\u0443\u043a\u0443)");
        }
    }

    // ---------- Крафт бура (shapeless через рецепт или команда give) ----------

    /** Выдаёт бур игроку с сообщением. */
    public void give(org.bukkit.entity.Player p, int tier) {
        ItemStack drill = createDrill(tier);
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(drill);
        for (ItemStack rem : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rem);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        Msg.send(p, "&a\u0412\u044b\u0434\u0430\u043d \u0431\u0443\u0440 &e\u0443\u0440\u043e\u0432\u043d\u044f " + tier + "&a!");
    }
}

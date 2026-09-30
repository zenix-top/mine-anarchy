package ru.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Сундуки на несколько страниц.
 * Ставится обычный сундук — он автоматически становится "многоэтажным":
 * при открытии появляется GUI с навигацией по страницам (6 строк + панель внизу).
 * Содержимое хранится в отдельном файле chests.yml и привязано к позиции блока,
 * поэтому переживает поломку/установку блока и перезагрузку сервера.
 */
public class ChestManager implements Listener {

    private final AnarchyCore plugin;
    private final Settings s;

    /** Ключ позиции -> страницы содержимого. */
    private final Map<String, List<ItemStack[]>> storage = new HashMap<>();
    /** Открытые инвентари: holder -> (позиция, страница). */
    private final Map<UUID, OpenChest> open = new HashMap<>();

    private File file;
    private YamlConfiguration yaml;

    private static final int PAGE_SIZE = 27;   // слоты 0..26
    private static final int INV_SIZE = 54;    // 6 строк: 27 контент + 18 пустых/декор + 9 панель

    private static class OpenChest {
        final String key;
        final Location loc;
        int page;
        OpenChest(String key, Location loc, int page) {
            this.key = key; this.loc = loc; this.page = page;
        }
    }

    public ChestManager(AnarchyCore plugin) {
        this.plugin = plugin;
        this.s = plugin.getSettings();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        load();
        plugin.getLogger().info("Многостраничные сундуки: активны, страниц на сундук: " + s.chestPages);
    }

    // ---------- Персистентность ----------

    private void load() {
        file = new File(plugin.getDataFolder(), "chests.yml");
        yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        storage.clear();
        for (String key : yaml.getKeys(false)) {
            List<ItemStack[]> pages = new ArrayList<>();
            for (int i = 0; i < s.chestPages; i++) {
                List<?> ser = yaml.getList(key + ".page" + i);
                ItemStack[] items = new ItemStack[PAGE_SIZE];
                if (ser != null) {
                    for (int slot = 0; slot < ser.size() && slot < PAGE_SIZE; slot++) {
                        Object o = ser.get(slot);
                        if (o instanceof ItemStack) items[slot] = (ItemStack) o;
                    }
                }
                pages.add(items);
            }
            storage.put(key, pages);
        }
    }

    public void save() {
        yaml.set("dummy", null);
        for (String k : yaml.getKeys(true)) yaml.set(k, null);
        for (Map.Entry<String, List<ItemStack[]>> e : storage.entrySet()) {
            List<ItemStack[]> pages = e.getValue();
            for (int i = 0; i < pages.size(); i++) {
                List<ItemStack> list = new ArrayList<>();
                for (ItemStack it : pages.get(i)) list.add(it);
                yaml.set(e.getKey() + ".page" + i, list);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить chests.yml", ex);
        }
    }

    // ---------- Логика ----------

    private List<ItemStack[]> pagesFor(Location loc, boolean create) {
        String key = DataStore.key(loc);
        List<ItemStack[]> pages = storage.get(key);
        if (pages == null && create) {
            pages = new ArrayList<>();
            for (int i = 0; i < s.chestPages; i++) pages.add(new ItemStack[PAGE_SIZE]);
            storage.put(key, pages);
        }
        return pages;
    }

    private boolean isChestBlock(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.ENDER_CHEST;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!s.bigChestsEnabled) return;
        if (!isChestBlock(e.getBlockPlaced().getType())) return;
        Player p = e.getPlayer();
        pagesFor(e.getBlock().getLocation(), true);
        plugin.getData().setChestOwner(e.getBlock().getLocation(), p.getUniqueId());
        plugin.getData().save();
        Msg.tell(p, "&a\u0421\u0443\u043d\u0434\u0443\u043a \u0441 &e" + s.chestPages +
                "&a \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0430\u043c\u0438 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent e) {
        if (!s.bigChestsEnabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var block = e.getClickedBlock();
        if (block == null || !isChestBlock(block.getType())) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        // отключаем ванильное открытие
        e.setCancelled(true);
        openChest(p, block.getLocation());
    }

    public void openChest(Player p, Location loc) {
        List<ItemStack[]> pages = pagesFor(loc, true);
        String key = DataStore.key(loc);
        Inventory inv = Bukkit.createInventory(new ChestHolder(key), INV_SIZE,
                Msg.color("&8\u0421\u0443\u043d\u0434\u0443\u043a &7[" + loc.getBlockX() + ", "
                        + loc.getBlockY() + ", " + loc.getBlockZ() + "]"));
        showPage(inv, pages, 0);
        open.put(p.getUniqueId(), new OpenChest(key, loc.clone(), 0));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1f);
    }

    private void showPage(Inventory inv, List<ItemStack[]> pages, int page) {
        page = Math.max(0, Math.min(pages.size() - 1, page));
        ItemStack[] content = pages.get(page);
        for (int i = 0; i < PAGE_SIZE; i++) inv.setItem(i, content[i]);
        // декор средней строки
        ItemStack pane = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), "&0");
        for (int i = PAGE_SIZE; i < INV_SIZE - 9; i++) inv.setItem(i, pane);
        // нижняя панель навигации
        inv.setItem(INV_SIZE - 9, arrow("\u25c0\u25c0", page - 2));   // назад 2
        inv.setItem(INV_SIZE - 8, arrow("\u25c0", page - 1));         // назад
        inv.setItem(INV_SIZE - 7, book(page, pages.size()));          // текущая страница
        inv.setItem(INV_SIZE - 6, arrow("\u25b6", page + 1));         // вперёд
        inv.setItem(INV_SIZE - 5, arrow("\u25b6\u25b6", page + 2));   // вперёд 2
        inv.setItem(INV_SIZE - 1, info(pages));                       // инфо/закрыть
    }

    private ItemStack arrow(String glyph, int targetPage) {
        ItemStack it = new ItemStack(targetPage >= 0 && targetPage < s.chestPages
                ? Material.ARROW : Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Msg.color("&e" + glyph + " &7стр. " + (targetPage + 1)));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack book(int page, int total) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Msg.color("&a&lСтраница " + (page + 1) + " &7/ " + total));
        List<String> lore = new ArrayList<>();
        lore.add("&7Клик по стрелкам внизу");
        lore.add("&7для переключения страниц.");
        m.setLore(lore.stream().map(Msg::color).collect(java.util.stream.Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack info(List<ItemStack[]> pages) {
        int used = 0;
        for (ItemStack[] pg : pages) for (ItemStack it : pg) if (it != null) used++;
        ItemStack it = new ItemStack(Material.OAK_SIGN);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Msg.color("&c\u2716 Закрыть"));
        List<String> lore = new ArrayList<>();
        lore.add("&7Предметов внутри: &e" + used);
        lore.add("&7Страниц: &e" + pages.size());
        m.setLore(lore.stream().map(Msg::color).collect(java.util.stream.Collectors.toList()));
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Msg.color(name));
        it.setItemMeta(m);
        return it;
    }

    /** Holder для различения нашего GUI от ванильного сундука. */
    public static class ChestHolder implements InventoryHolder {
        public final String key;
        ChestHolder(String key) { this.key = key; }
        @Override
        public Inventory getInventory() { return null; }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChestHolder)) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        OpenChest oc = open.get(p.getUniqueId());
        if (oc == null) return;
        List<ItemStack[]> pages = storage.get(oc.key);
        if (pages == null) return;

        int raw = e.getRawSlot();
        int size = e.getInventory().getSize();
        // клик по панели навигации (нижние 9 слотов GUI) — блокируем и листаем
        if (raw >= size - 9 && raw < size) {
            e.setCancelled(true);
            int slot = raw - (size - 9);
            int np = oc.page;
            switch (slot) {
                case 0: np -= 2; break;
                case 1: np -= 1; break;
                case 3: np += 1; break;
                case 4: np += 2; break;
                case 8: p.closeInventory(); return;
                default: return;
            }
            np = Math.max(0, Math.min(pages.size() - 1, np));
            if (np == oc.page) return;
            saveCurrent(p, pages, oc.page);
            oc.page = np;
            showPage(e.getInventory(), pages, np);
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1f);
            return;
        }
        // клик по контенту сундука или своему инвентарю — разрешаем перемещение предметов
        if (raw >= 0 && raw < PAGE_SIZE) {
            saveCurrent(p, pages, oc.page);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ChestHolder) {
            e.setCancelled(true);
        }
    }

    private void saveCurrent(Player p, List<ItemStack[]> pages, int page) {
        Inventory inv = p.getOpenInventory().getTopInventory();
        if (inv.getSize() < PAGE_SIZE) return;
        ItemStack[] arr = pages.get(page);
        for (int i = 0; i < PAGE_SIZE; i++) arr[i] = inv.getItem(i);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChestHolder)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        OpenChest oc = open.remove(p.getUniqueId());
        if (oc != null) {
            saveCurrent(p, storage.computeIfAbsent(oc.key, k -> pagesFor(oc.loc, true)), oc.page);
            save();
        }
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1f);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChestBreak(BlockBreakEvent e) {
        if (!s.bigChestsEnabled) return;
        if (!isChestBlock(e.getBlock().getType())) return;
        Location loc = e.getBlock().getLocation();
        String key = DataStore.key(loc);
        List<ItemStack[]> pages = storage.remove(key);
        Player p = e.getPlayer();
        if (pages != null) {
            int returned = 0;
            for (ItemStack[] pg : pages) {
                for (ItemStack it : pg) {
                    if (it == null) continue;
                    returned++;
                    loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.3, 0.5), it);
                }
            }
            save();
            if (returned > 0) Msg.tell(p, "&e\u0418\u0437 \u0441\u0443\u043d\u0434\u0443\u043a\u0430 \u0432\u044b\u043f\u0430\u043b\u043e \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432: &c" + returned);
        }
        plugin.getData().removeChest(loc);
        plugin.getData().save();
    }

    /** Возвращает число занятых слотов во всех страницах сундука по локации. */
    public int countItems(Location loc) {
        List<ItemStack[]> pages = storage.get(DataStore.key(loc));
        if (pages == null) return 0;
        int n = 0;
        for (ItemStack[] pg : pages) for (ItemStack it : pg) if (it != null) n++;
        return n;
    }
}

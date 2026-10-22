package me.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;

import java.util.*;

public class Gui implements Listener {
    public enum Type { MODES, DUEL_MODES, EDITOR_MODES, EDITOR, LIBRARY }

    public static class Holder implements InventoryHolder {
        Type type; Mode mode; int page; UUID target; Inventory inv;
        Holder(Type t) { type = t; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static class Staged { Mode mode; ItemStack[] layout; }

    private final ArenaCore pl;
    private final Map<UUID, Staged> staged = new HashMap<>();

    public Gui(ArenaCore pl) { this.pl = pl; }

    private static Component name(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material m, String n, NamedTextColor c, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta im = s.getItemMeta();
        im.displayName(name(n, c));
        if (lore.length > 0) im.lore(Arrays.stream(lore).map(x -> name(x, NamedTextColor.GRAY)).toList());
        s.setItemMeta(im);
        return s;
    }

    // ---------- mode selection menus ----------
    public void openModes(Player p, Type type, UUID target) {
        Holder h = new Holder(type);
        h.target = target;
        String title = type == Type.MODES ? "Select Gamemode" : type == Type.DUEL_MODES ? "Duel: pick a mode" : "Kit Editor: pick a mode";
        h.inv = Bukkit.createInventory(h, 27, Component.text(title));
        Mode[] all = Mode.values();
        for (int i = 0; i < all.length; i++) {
            h.inv.setItem(9 + i, named(all[i].icon, all[i].display, NamedTextColor.AQUA, "Click to select"));
        }
        p.openInventory(h.inv);
    }

    // ---------- kit editor ----------
    private static int map(int g) {
        if (g < 27) return 9 + g;
        if (g < 36) return g - 27;
        return switch (g) { case 36 -> 39; case 37 -> 38; case 38 -> 37; case 39 -> 36; case 40 -> 40; default -> -1; };
    }

    public void openEditor(Player p, Mode m) {
        Staged s = staged.get(p.getUniqueId());
        if (s == null || s.mode != m) {
            s = new Staged();
            s.mode = m;
            s.layout = pl.layoutFor(p.getUniqueId(), m);
            staged.put(p.getUniqueId(), s);
        }
        Holder h = new Holder(Type.EDITOR);
        h.mode = m;
        h.inv = Bukkit.createInventory(h, 54, Component.text("Editing: " + m.display));
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 41; i < 54; i++) h.inv.setItem(i, pane);
        for (int g = 0; g <= 40; g++) {
            ItemStack it = s.layout[map(g)];
            h.inv.setItem(g, it == null ? null : it.clone());
        }
        h.inv.setItem(45, named(Material.LIME_CONCRETE, "SAVE", NamedTextColor.GREEN, "Save this kit layout"));
        h.inv.setItem(46, named(Material.RED_CONCRETE, "RESET", NamedTextColor.RED, "Back to the default kit"));
        h.inv.setItem(47, named(Material.CHEST, "ADD ITEMS", NamedTextColor.GOLD, "All items + enchanted items for " + m.display));
        h.inv.setItem(48, named(Material.BARRIER, "TRASH", NamedTextColor.RED, "Click with an item on your cursor to delete it"));
        h.inv.setItem(49, named(Material.PAPER, "Layout", NamedTextColor.AQUA, "Rows = inventory, bottom row = hotbar", "Right of that: armor + offhand", "Drag items to rearrange"));
        h.inv.setItem(53, named(Material.ARROW, "BACK", NamedTextColor.YELLOW, "Close editor"));
        p.openInventory(h.inv);
    }

    private void capture(Inventory inv, Staged s) {
        for (int g = 0; g <= 40; g++) {
            ItemStack it = inv.getItem(g);
            s.layout[map(g)] = it == null || it.getType().isAir() ? null : it.clone();
        }
    }

    private void openLibrary(Player p, Mode m, int page) {
        List<ItemStack> lib = Kits.library(m);
        int pages = Math.max(1, (lib.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(Type.LIBRARY);
        h.mode = m;
        h.page = page;
        h.inv = Bukkit.createInventory(h, 54, Component.text("Items: " + m.display + " (" + (page + 1) + "/" + pages + ")"));
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx < lib.size()) h.inv.setItem(i, lib.get(idx));
        }
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 45; i < 54; i++) h.inv.setItem(i, pane);
        h.inv.setItem(45, named(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        h.inv.setItem(49, named(Material.BOOK, "Back to editor", NamedTextColor.GOLD, "Click an item to add it to your kit"));
        h.inv.setItem(53, named(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        p.openInventory(h.inv);
    }

    private void addToStage(Staged s, ItemStack item) {
        ItemStack c = item.clone();
        String n = c.getType().name();
        int slot = -1;
        if (n.endsWith("_HELMET")) slot = 39;
        else if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) slot = 38;
        else if (n.endsWith("_LEGGINGS")) slot = 37;
        else if (n.endsWith("_BOOTS")) slot = 36;
        if (slot != -1) {
            ItemStack old = s.layout[slot];
            s.layout[slot] = c;
            if (old != null) putFirstEmpty(s, old);    // swapped-out armor goes to inventory
            return;
        }
        putFirstEmpty(s, c);
    }

    private void putFirstEmpty(Staged s, ItemStack c) {
        for (int i = 0; i < 36; i++) if (s.layout[i] == null) { s.layout[i] = c; return; }
    }

    // ---------- events ----------
    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();

        if (h.type == Type.MODES || h.type == Type.DUEL_MODES || h.type == Type.EDITOR_MODES) {
            e.setCancelled(true);
            if (raw < 9 || raw > 17) return;
            Mode[] all = Mode.values();
            if (raw - 9 >= all.length) return;
            Mode m = all[raw - 9];
            p.closeInventory();
            switch (h.type) {
                case MODES -> pl.joinMode(p, m);
                case DUEL_MODES -> pl.sendRequest(p, h.target, m);
                default -> pl.getServer().getScheduler().runTask(pl, () -> openEditor(p, m));
            }
            return;
        }

        if (h.type == Type.LIBRARY) {
            e.setCancelled(true);
            if (raw < 0 || raw >= top.getSize()) return;
            Staged s = staged.get(p.getUniqueId());
            if (raw < 45) {
                ItemStack it = top.getItem(raw);
                if (it != null && s != null) { addToStage(s, it); pl.msg(p, "Added to your " + h.mode.display + " kit. Press the book to go back and save."); }
            } else if (raw == 45) pl.getServer().getScheduler().runTask(pl, () -> openLibrary(p, h.mode, h.page - 1));
            else if (raw == 53) pl.getServer().getScheduler().runTask(pl, () -> openLibrary(p, h.mode, h.page + 1));
            else if (raw == 49) pl.getServer().getScheduler().runTask(pl, () -> openEditor(p, h.mode));
            return;
        }

        // EDITOR
        Staged s = staged.get(p.getUniqueId());
        if (raw >= top.getSize() || raw < 0) { e.setCancelled(true); return; }   // no touching real inventory
        ClickType ct = e.getClick();
        if (e.isShiftClick() || ct == ClickType.NUMBER_KEY || ct == ClickType.SWAP_OFFHAND || ct == ClickType.DOUBLE_CLICK) { e.setCancelled(true); return; }
        if (raw <= 40) return;      // normal item movement inside the layout
        e.setCancelled(true);
        if (s == null) return;
        switch (raw) {
            case 45 -> {
                capture(top, s);
                pl.saveLayout(p.getUniqueId(), h.mode, s.layout);
                pl.msg(p, h.mode.display + " kit saved!");
            }
            case 46 -> {
                pl.resetLayout(p.getUniqueId(), h.mode);
                staged.remove(p.getUniqueId());
                pl.getServer().getScheduler().runTask(pl, () -> openEditor(p, h.mode));
            }
            case 47 -> {
                capture(top, s);
                pl.getServer().getScheduler().runTask(pl, () -> openLibrary(p, h.mode, 0));
            }
            case 48 -> p.setItemOnCursor(null);
            case 53 -> pl.getServer().getScheduler().runTask(pl, () -> { staged.remove(p.getUniqueId()); p.closeInventory(); });
            default -> {}
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        if (h.type != Type.EDITOR) { e.setCancelled(true); return; }
        int size = e.getView().getTopInventory().getSize();
        for (int r : e.getRawSlots()) if (r > 40) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (h.type == Type.EDITOR) {
            Staged s = staged.get(p.getUniqueId());
            if (s != null) capture(e.getInventory(), s);
        }
        p.setItemOnCursor(null);   // never let editor items leak into the real inventory
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent e) { staged.remove(e.getPlayer().getUniqueId()); }
}

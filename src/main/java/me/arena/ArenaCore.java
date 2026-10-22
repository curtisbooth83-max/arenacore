package me.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ArenaCore extends JavaPlugin {

    public final Map<UUID, Mode> ffa = new HashMap<>();
    public final Map<UUID, Duel> duels = new HashMap<>();
    public final Map<UUID, Request> requests = new HashMap<>();
    public final Set<String> busy = new HashSet<>();
    public NamespacedKey lobbyKey;
    public Gui gui;
    public Protection protection;
    private FileConfiguration data;
    private File dataFile;

    public record Duel(UUID a, UUID b, Mode mode, String arena) {
        public UUID other(UUID u) { return u.equals(a) ? b : a; }
        public boolean has(UUID u) { return u.equals(a) || u.equals(b); }
    }

    public record Request(UUID from, Mode mode, long time) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobbyKey = new NamespacedKey(this, "lobbyitem");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        gui = new Gui(this);
        protection = new Protection(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(gui, this);
        pm.registerEvents(protection, this);
        pm.registerEvents(new GameListener(this), this);
        if (getConfig().getBoolean("anticheat.enabled", true)) pm.registerEvents(new AntiCheat(this), this);
        getLogger().info("ArenaCore enabled. Existing worlds/plugins are untouched; data lives in plugins/ArenaCore/.");
    }

    @Override
    public void onDisable() {
        if (protection != null) protection.restoreAll();
        saveData();
    }

    // ---------- helpers ----------
    public void msg(CommandSender s, String t) {
        s.sendMessage(Component.text("[Arena] " + t, NamedTextColor.AQUA));
    }

    public boolean inMatch(UUID u) { return ffa.containsKey(u) || duels.containsKey(u); }

    private void saveData() {
        try { data.save(dataFile); } catch (IOException ex) { getLogger().warning("Could not save data.yml: " + ex.getMessage()); }
    }

    private void setLoc(String path, Location l) {
        getConfig().set(path, l);
        saveConfig();
    }

    public Location lobby(Player p) {
        Location l = getConfig().getLocation("lobby");
        return l != null ? l : p.getWorld().getSpawnLocation();
    }

    public ItemStack lobbyItem(Material m, String name, NamedTextColor color, String tag) {
        ItemStack s = new ItemStack(m);
        ItemMeta im = s.getItemMeta();
        im.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        im.setUnbreakable(true);
        im.getPersistentDataContainer().set(lobbyKey, PersistentDataType.STRING, tag);
        s.setItemMeta(im);
        return s;
    }

    // ---------- kit storage ----------
    public ItemStack[] layoutFor(UUID u, Mode m) {
        List<?> raw = data.getList("layouts." + u + "." + m.name());
        if (raw == null) return Kits.def(m);
        ItemStack[] out = new ItemStack[41];
        for (int i = 0; i < 41 && i < raw.size(); i++) if (raw.get(i) instanceof ItemStack s) out[i] = s;
        return out;
    }

    public void saveLayout(UUID u, Mode m, ItemStack[] layout) {
        data.set("layouts." + u + "." + m.name(), Arrays.asList(layout));
        saveData();
    }

    public void resetLayout(UUID u, Mode m) {
        data.set("layouts." + u + "." + m.name(), null);
        saveData();
    }

    private void backupInventory(Player p) {
        String key = "backup." + p.getUniqueId();
        if (data.contains(key)) return;
        ItemStack[] c = p.getInventory().getContents();
        boolean any = false;
        for (ItemStack s : c) if (s != null && !s.getType().isAir()) any = true;
        if (!any) return;
        data.set(key, Arrays.asList(c));   // original inventory kept safe in data.yml (first join only)
        saveData();
    }

    // ---------- state changes ----------
    public void sendToLobby(Player p) {
        ffa.remove(p.getUniqueId());
        backupInventory(p);
        p.closeInventory();
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (var pe : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(pe.getType());
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.teleport(lobby(p));
        inv.setItem(0, lobbyItem(Material.NETHERITE_SWORD, "Select Gamemode", NamedTextColor.AQUA, "modes"));
        inv.setItem(8, lobbyItem(Material.BOOK, "Kit Editor", NamedTextColor.GOLD, "editor"));
        inv.setHeldItemSlot(0);
    }

    public void applyKit(Player p, Mode m) {
        p.closeInventory();
        for (var pe : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(pe.getType());
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] l = layoutFor(p.getUniqueId(), m);
        ItemStack[] copy = new ItemStack[41];
        for (int i = 0; i < 41; i++) copy[i] = l[i] == null ? null : l[i].clone();
        inv.setContents(copy);
        inv.setHeldItemSlot(0);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    public void joinMode(Player p, Mode m) {
        Location l = getConfig().getLocation("arenas." + m.name());
        if (l == null) { msg(p, m.display + " arena isn't set up yet (admin: /setarena " + m.name().toLowerCase() + ")."); return; }
        ffa.put(p.getUniqueId(), m);
        applyKit(p, m);
        p.teleport(l);
        msg(p, "Joined " + m.display + ". Use /lobby to leave.");
    }

    // ---------- duels ----------
    public void sendRequest(Player from, UUID targetId, Mode mode) {
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { msg(from, "That player is gone."); return; }
        requests.put(targetId, new Request(from.getUniqueId(), mode, System.currentTimeMillis()));
        msg(from, "Duel request (" + mode.display + ") sent to " + t.getName() + ".");
        t.sendMessage(Component.text("[Arena] " + from.getName() + " challenges you to a " + mode.display + " duel! ", NamedTextColor.AQUA)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/duel accept " + from.getName())))
                .append(Component.text(" "))
                .append(Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/duel deny"))));
    }

    private void acceptDuel(Player target, String fromName) {
        Request r = requests.get(target.getUniqueId());
        Player from = Bukkit.getPlayerExact(fromName);
        if (r == null || from == null || !r.from().equals(from.getUniqueId()) || System.currentTimeMillis() - r.time() > 60_000) {
            msg(target, "No valid duel request from that player."); return;
        }
        requests.remove(target.getUniqueId());
        if (inMatch(from.getUniqueId()) || inMatch(target.getUniqueId())) { msg(target, "Both players must be in the lobby."); return; }
        String arena = null;
        ConfigurationSection cs = getConfig().getConfigurationSection("duelarenas");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                if (!busy.contains(k) && cs.getLocation(k + ".p1") != null && cs.getLocation(k + ".p2") != null) { arena = k; break; }
            }
        }
        if (arena == null) { msg(target, "No free duel arena (admin: /setduel <name> 1 and 2)."); msg(from, "No free duel arena."); return; }
        busy.add(arena);
        Duel d = new Duel(from.getUniqueId(), target.getUniqueId(), r.mode(), arena);
        duels.put(from.getUniqueId(), d);
        duels.put(target.getUniqueId(), d);
        applyKit(from, r.mode());
        applyKit(target, r.mode());
        from.teleport(cs.getLocation(arena + ".p1"));
        target.teleport(cs.getLocation(arena + ".p2"));
        msg(from, "Duel started! Good luck."); msg(target, "Duel started! Good luck.");
    }

    public void endDuel(Duel d, UUID winner) {
        duels.remove(d.a());
        duels.remove(d.b());
        busy.remove(d.arena());
        Player w = Bukkit.getPlayer(winner);
        Player l = Bukkit.getPlayer(d.other(winner));
        if (w != null) msg(w, "You won the duel!");
        if (l != null) msg(l, "You lost the duel.");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player w2 = Bukkit.getPlayer(winner);
            if (w2 != null && !w2.isDead()) sendToLobby(w2);
        }, 60L);
    }

    // ---------- commands ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { msg(sender, "Players only."); return true; }
        switch (cmd.getName().toLowerCase()) {
            case "setlobby" -> {
                if (!p.hasPermission("arena.admin")) { msg(p, "No permission."); return true; }
                setLoc("lobby", p.getLocation());
                msg(p, "Lobby spawn set.");
            }
            case "setarena" -> {
                if (!p.hasPermission("arena.admin")) { msg(p, "No permission."); return true; }
                Mode m = args.length > 0 ? Mode.parse(args[0]) : null;
                if (m == null) { msg(p, "Usage: /setarena <" + String.join("|", Arrays.stream(Mode.values()).map(x -> x.name().toLowerCase()).toList()) + ">"); return true; }
                setLoc("arenas." + m.name(), p.getLocation());
                msg(p, m.display + " arena spawn set.");
            }
            case "setduel" -> {
                if (!p.hasPermission("arena.admin")) { msg(p, "No permission."); return true; }
                if (args.length < 2 || !(args[1].equals("1") || args[1].equals("2"))) { msg(p, "Usage: /setduel <arenaName> <1|2>"); return true; }
                setLoc("duelarenas." + args[0] + ".p" + args[1], p.getLocation());
                msg(p, "Duel arena " + args[0] + " spawn " + args[1] + " set.");
            }
            case "kiteditor" -> gui.openModes(p, Gui.Type.EDITOR_MODES, null);
            case "lobby" -> {
                if (duels.containsKey(p.getUniqueId())) msg(p, "You can't leave a duel.");
                else sendToLobby(p);
            }
            case "duel" -> duelCmd(p, args);
        }
        return true;
    }

    private void duelCmd(Player p, String[] args) {
        if (args.length == 0) { msg(p, "Usage: /duel <player>"); return; }
        if (args[0].equalsIgnoreCase("deny")) { requests.remove(p.getUniqueId()); msg(p, "Request denied."); return; }
        if (args[0].equalsIgnoreCase("accept") && args.length > 1) { acceptDuel(p, args[1]); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null || t == p) { msg(p, "Player not found."); return; }
        if (inMatch(p.getUniqueId()) || inMatch(t.getUniqueId())) { msg(p, "Both players must be in the lobby."); return; }
        gui.openModes(p, Gui.Type.DUEL_MODES, t.getUniqueId());
    }
}

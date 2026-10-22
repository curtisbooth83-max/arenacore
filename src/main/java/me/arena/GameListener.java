package me.arena;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class GameListener implements Listener {
    private final ArenaCore pl;

    public GameListener(ArenaCore pl) { this.pl = pl; }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("arena.build");
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        pl.sendToLobby(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        ArenaCore.Duel d = pl.duels.get(u);
        if (d != null) pl.endDuel(d, d.other(u));
        pl.ffa.remove(u);
        pl.requests.remove(u);
    }

    // ----- lobby items -----
    @EventHandler
    public void use(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (pl.inMatch(p.getUniqueId())) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            if (!builder(p)) e.setCancelled(true);
            return;
        }
        if (!builder(p)) e.setCancelled(true);
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String tag = it.getItemMeta().getPersistentDataContainer().get(pl.lobbyKey, PersistentDataType.STRING);
        if (tag == null) return;
        if (tag.equals("modes")) pl.gui.openModes(p, Gui.Type.MODES, null);
        else if (tag.equals("editor")) pl.gui.openModes(p, Gui.Type.EDITOR_MODES, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) { if (!builder(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent e) { if (!pl.inMatch(e.getPlayer().getUniqueId()) && !builder(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && !pl.inMatch(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void itemDamage(PlayerItemDamageEvent e) {
        if (!pl.inMatch(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void lobbyClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui.Holder) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!pl.inMatch(p.getUniqueId()) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void lobbyDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui.Holder) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!pl.inMatch(p.getUniqueId()) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler
    public void voidFall(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (e.getTo() != null && e.getTo().getY() < e.getTo().getWorld().getMinHeight() - 10 && !pl.inMatch(p.getUniqueId())) {
            p.teleport(pl.lobby(p));
        }
    }

    // ----- PvP rules -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void anyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player v && !pl.inMatch(v.getUniqueId())) e.setCancelled(true);   // lobby: no damage, no PvP
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Player a = null;
        if (e.getDamager() instanceof Player x) a = x;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player x) a = x;
        if (a == null || a == v) return;
        ArenaCore.Duel dv = pl.duels.get(v.getUniqueId());
        ArenaCore.Duel da = pl.duels.get(a.getUniqueId());
        if (dv != null || da != null) {
            if (dv == null || dv != da) e.setCancelled(true);
        } else if (!(pl.ffa.containsKey(v.getUniqueId()) && pl.ffa.containsKey(a.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        Player v = e.getPlayer();
        e.getDrops().clear();
        e.setDroppedExp(0);
        ArenaCore.Duel d = pl.duels.get(v.getUniqueId());
        if (d != null) pl.endDuel(d, d.other(v.getUniqueId()));
        pl.getServer().getScheduler().runTaskLater(pl, () -> { if (v.isOnline() && v.isDead()) v.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        pl.ffa.remove(p.getUniqueId());
        e.setRespawnLocation(pl.lobby(p));
        pl.getServer().getScheduler().runTask(pl, () -> pl.sendToLobby(p));
    }
}

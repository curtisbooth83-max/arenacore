package me.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps can't be destroyed: no breaking/placing, explosions break nothing, no fire, no fluid flow.
 * Only exception: blocks placed by players during a match are tracked, can be broken, and are
 * restored to what was there before after a few seconds.
 */
public class Protection implements Listener {
    private final ArenaCore pl;
    private final Map<Location, BlockData> placed = new HashMap<>();

    public Protection(ArenaCore pl) { this.pl = pl; }

    private boolean bypass(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("arena.build");
    }

    public void restoreAll() {
        for (Map.Entry<Location, BlockData> e : placed.entrySet()) e.getKey().getBlock().setBlockData(e.getValue(), false);
        placed.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (bypass(e.getPlayer())) return;
        Location l = e.getBlock().getLocation();
        if (placed.containsKey(l)) {
            BlockData orig = placed.remove(l);
            e.setDropItems(false);
            e.setCancelled(true);
            e.getBlock().setBlockData(orig, false);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        if (!pl.inMatch(p.getUniqueId())) { e.setCancelled(true); return; }
        Material old = e.getBlockReplacedState().getType();
        if (!old.isAir()) { e.setCancelled(true); return; }   // never overwrite real map blocks
        Location l = e.getBlock().getLocation();
        placed.put(l, e.getBlockReplacedState().getBlockData());
        long ticks = pl.getConfig().getLong("temp-block-seconds", 20) * 20L;
        pl.getServer().getScheduler().runTaskLater(pl, () -> {
            BlockData orig = placed.remove(l);
            if (orig != null) l.getBlock().setBlockData(orig, false);
        }, ticks);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true) public void burn(BlockBurnEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void ignite(BlockIgniteEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void spread(BlockSpreadEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void flow(BlockFromToEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void decay(LeavesDecayEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void grow(StructureGrowEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void pistonOut(BlockPistonExtendEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void pistonIn(BlockPistonRetractEvent e) { e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void entityBlock(EntityChangeBlockEvent e) { e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent e) { if (!bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent e) { if (!bypass(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void physical(PlayerInteractEvent e) {
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hangingBreak(HangingBreakEvent e) { e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void hitDecor(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Hanging || e.getEntity() instanceof ArmorStand) {
            if (e.getDamager() instanceof Player p && bypass(p)) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent e) { if (!bypass(e.getPlayer())) e.setCancelled(true); }
}

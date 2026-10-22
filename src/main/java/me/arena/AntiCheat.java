package me.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Built-in basic anticheat (speed, fly/hover, reach, kill-aura angle + multi-aura, autoclicker).
 * It can't catch every client-side cheat, so keep it alongside a dedicated anticheat if you can.
 */
public class AntiCheat implements Listener {
    private static class D {
        double vl;
        long lastVel, lastTp, lastHit;
        UUID lastTarget;
        int hover;
        double groundY;
        final ArrayDeque<Long> clicks = new ArrayDeque<>();
    }

    private final ArenaCore pl;
    private final Map<UUID, D> data = new HashMap<>();
    private final double kickVl, maxReach, spearReach;
    private final int maxCps;

    public AntiCheat(ArenaCore pl) {
        this.pl = pl;
        kickVl = pl.getConfig().getDouble("anticheat.kick-vl", 30);
        maxReach = pl.getConfig().getDouble("anticheat.max-reach", 3.6);
        spearReach = pl.getConfig().getDouble("anticheat.spear-reach", 6.0);
        maxCps = pl.getConfig().getInt("anticheat.max-cps", 22);
        Bukkit.getScheduler().runTaskTimer(pl, () -> data.values().forEach(d -> d.vl = Math.max(0, d.vl - 1)), 200L, 200L);
    }

    private D d(Player p) { return data.computeIfAbsent(p.getUniqueId(), k -> new D()); }

    private boolean exempt(Player p) {
        return p.hasPermission("arena.bypass") || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private void flag(Player p, String check, String info, double add) {
        D d = d(p);
        d.vl += add;
        String m = "[AC] " + p.getName() + " failed " + check + " (" + info + ") VL " + String.format("%.1f", d.vl);
        pl.getLogger().info(m);
        for (Player s : Bukkit.getOnlinePlayers()) if (s.hasPermission("arena.alerts")) s.sendMessage(Component.text(m, NamedTextColor.RED));
        if (d.vl >= kickVl) {
            d.vl = 0;
            Bukkit.getScheduler().runTask(pl, () -> p.kick(Component.text("Unfair advantage detected (" + check + ").")));
        }
    }

    // ----- grace windows -----
    @EventHandler public void vel(PlayerVelocityEvent e) { d(e.getPlayer()).lastVel = System.currentTimeMillis(); }
    @EventHandler public void tp(PlayerTeleportEvent e) { d(e.getPlayer()).lastTp = System.currentTimeMillis(); }
    @EventHandler public void hurt(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) d(p).lastVel = System.currentTimeMillis(); }
    @EventHandler public void quit(PlayerQuitEvent e) { data.remove(e.getPlayer().getUniqueId()); }

    private static boolean climbable(Material m) {
        String n = m.name();
        return n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING") || n.equals("COBWEB")
                || n.contains("WATER") || n.contains("LAVA") || n.equals("BUBBLE_COLUMN") || n.contains("HONEY") || n.equals("POWDER_SNOW");
    }

    // ----- movement: speed + fly -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || f.getWorld() != t.getWorld()) return;
        if (exempt(p) || p.getAllowFlight() || p.isInsideVehicle() || p.isGliding() || p.isRiptiding() || p.isSwimming()) return;
        D d = d(p);
        long now = System.currentTimeMillis();
        if (p.isOnGround()) { d.groundY = t.getY(); d.hover = 0; }
        if (now - d.lastVel < 1500 || now - d.lastTp < 1500) { d.groundY = t.getY(); d.hover = 0; return; }

        // Speed
        double h = Math.hypot(t.getX() - f.getX(), t.getZ() - f.getZ());
        double allowed = 0.75 * (p.getWalkSpeed() / 0.2);
        PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
        if (sp != null) allowed += 0.15 * (sp.getAmplifier() + 1);
        if (p.getLocation().subtract(0, 0.5, 0).getBlock().getType().name().contains("ICE")) allowed += 0.4;
        if (h > allowed) {
            flag(p, "Speed", String.format("%.2f > %.2f", h, allowed), 2);
            e.setTo(f);
            return;
        }

        // Fly / hover
        Material in = t.getBlock().getType();
        Material below = t.clone().subtract(0, 0.3, 0).getBlock().getType();
        if (climbable(in) || climbable(below) || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) { d.hover = 0; d.groundY = t.getY(); return; }
        double dy = t.getY() - f.getY();
        if (!p.isOnGround() && Math.abs(dy) < 0.03) {
            if (++d.hover > 10) { flag(p, "Fly", "hovering " + d.hover + " ticks", 3); d.hover = 0; e.setTo(f); return; }
        } else d.hover = 0;
        double maxRise = 2.0;
        PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jb != null) maxRise += 1.2 * (jb.getAmplifier() + 1);
        if (!p.isOnGround() && t.getY() - d.groundY > maxRise) {
            flag(p, "Fly", String.format("rose %.1f blocks", t.getY() - d.groundY), 3);
            e.setTo(f);
        }
    }

    // ----- combat: reach, angle, multi-target -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (exempt(p)) return;
        Entity v = e.getEntity();
        D d = d(p);

        Vector eye = p.getEyeLocation().toVector();
        BoundingBox bb = v.getBoundingBox();
        Vector near = new Vector(
                Math.max(bb.getMinX(), Math.min(eye.getX(), bb.getMaxX())),
                Math.max(bb.getMinY(), Math.min(eye.getY(), bb.getMaxY())),
                Math.max(bb.getMinZ(), Math.min(eye.getZ(), bb.getMaxZ())));
        double dist = eye.distance(near);
        boolean spear = p.getInventory().getItemInMainHand().getType().name().endsWith("_SPEAR");
        double lim = spear ? spearReach : maxReach;
        if (dist > lim) {
            flag(p, "Reach", String.format("%.2f > %.2f", dist, lim), 1.5);
            e.setCancelled(true);
            return;
        }

        if (dist > 1.5) {
            Vector to = bb.getCenter().subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.dot(p.getEyeLocation().getDirection())))));
            if (angle > 95) { flag(p, "KillAura", String.format("angle %.0f", angle), 3); e.setCancelled(true); return; }
            if (angle > 70) flag(p, "KillAura", String.format("angle %.0f", angle), 1);
        }

        long now = System.currentTimeMillis();
        if (d.lastTarget != null && !d.lastTarget.equals(v.getUniqueId()) && now - d.lastHit < 45) {
            flag(p, "MultiAura", "2 targets in " + (now - d.lastHit) + "ms", 3);
            e.setCancelled(true);
        }
        d.lastTarget = v.getUniqueId();
        d.lastHit = now;
    }

    // ----- autoclicker -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        D d = d(p);
        long now = System.currentTimeMillis();
        d.clicks.addLast(now);
        while (!d.clicks.isEmpty() && now - d.clicks.peekFirst() > 1000) d.clicks.pollFirst();
        if (d.clicks.size() > maxCps) {
            flag(p, "AutoClicker", d.clicks.size() + " CPS", 1);
            e.setCancelled(true);
        }
    }
}

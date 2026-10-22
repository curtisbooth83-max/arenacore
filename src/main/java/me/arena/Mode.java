package me.arena;

import org.bukkit.Material;

public enum Mode {
    CRYSTAL("Crystal PvP", "END_CRYSTAL"),
    MACE("Mace PvP", "MACE"),
    ELYTRA_MACE("Elytra Mace", "ELYTRA"),
    ROCKET_MACE("Rocket Mace", "FIREWORK_ROCKET"),
    SPEAR_MACE("Spear Mace", "NETHERITE_SPEAR"),
    SWORD("Sword PvP", "NETHERITE_SWORD"),
    AXE("Axe PvP", "NETHERITE_AXE"),
    UHC("UHC", "GOLDEN_APPLE"),
    CREEPER("Creeper PvP", "CREEPER_SPAWN_EGG");

    public final String display;
    public final Material icon;

    Mode(String display, String mat) {
        this.display = display;
        Material m = Material.matchMaterial(mat);
        this.icon = m == null ? Material.IRON_SWORD : m;
    }

    public static Mode parse(String s) {
        if (s == null) return null;
        for (Mode m : values()) if (m.name().equalsIgnoreCase(s.replace(' ', '_'))) return m;
        return null;
    }
}

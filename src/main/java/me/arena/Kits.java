package me.arena;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/** Default kits + the item library used by the kit editor. Layout array: 0-8 hotbar, 9-35 main, 36 boots, 37 legs, 38 chest, 39 helmet, 40 offhand. */
public final class Kits {
    private Kits() {}

    static final String A = "unbreaking:3,mending:1";
    static final String SWORD = "sharpness:5,fire_aspect:2,sweeping_edge:3,looting:3," + A;

    public static ItemStack it(Material m, int amt, String ench) {
        ItemStack s = new ItemStack(m, amt);
        if (ench != null && !ench.isEmpty()) {
            ItemMeta meta = s.getItemMeta();
            for (String e : ench.split(",")) {
                String[] p = e.split(":");
                Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
                if (en != null) meta.addEnchant(en, Integer.parseInt(p[1]), true);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    public static ItemStack it(String name, int amt, String ench) {
        Material m = Material.matchMaterial(name);
        return m == null ? null : it(m, amt, ench);
    }

    public static ItemStack it(Material m, int amt) { return new ItemStack(m, amt); }

    public static ItemStack potion(Material type, PotionType pt, int amt) {
        ItemStack s = new ItemStack(type, amt);
        PotionMeta pm = (PotionMeta) s.getItemMeta();
        pm.setBasePotionType(pt);
        s.setItemMeta(pm);
        return s;
    }

    public static ItemStack rockets(int amt) {
        ItemStack s = new ItemStack(Material.FIREWORK_ROCKET, amt);
        FireworkMeta fm = (FireworkMeta) s.getItemMeta();
        fm.setPower(3);
        s.setItemMeta(fm);
        return s;
    }

    static void armor(ItemStack[] c, String tier, String ench, String bootsExtra) {
        c[39] = it(tier + "_HELMET", 1, ench + ",respiration:3,aqua_affinity:1");
        c[38] = it(tier + "_CHESTPLATE", 1, ench);
        c[37] = it(tier + "_LEGGINGS", 1, ench);
        c[36] = it(tier + "_BOOTS", 1, ench + "," + bootsExtra);
    }

    /** Puts totems in the offhand first, then main inventory from slot 9. */
    static void totems(ItemStack[] c, int total) {
        int left = total;
        if (c[40] == null) { c[40] = new ItemStack(Material.TOTEM_OF_UNDYING); left--; }
        for (int i = 9; i < 36 && left > 0; i++) {
            if (c[i] == null) { c[i] = new ItemStack(Material.TOTEM_OF_UNDYING); left--; }
        }
    }

    /** Healing splash potions at the end of the main inventory. */
    static void heals(ItemStack[] c, int n) {
        for (int i = 35; i >= 9 && n > 0; i--) {
            if (c[i] == null) { c[i] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1); n--; }
        }
    }

    public static ItemStack[] def(Mode m) {
        ItemStack[] c = new ItemStack[41];
        String P = "protection:4," + A;
        switch (m) {
            case CRYSTAL -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[0] = it("NETHERITE_SWORD", 1, SWORD);
                c[1] = it(Material.END_CRYSTAL, 64);
                c[2] = it(Material.OBSIDIAN, 64);
                c[3] = it(Material.GOLDEN_APPLE, 64);
                c[4] = it(Material.ENDER_PEARL, 16);
                c[5] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1);
                c[7] = it(Material.EXPERIENCE_BOTTLE, 64);
                c[8] = it(Material.OBSIDIAN, 64);
                totems(c, 15);              // 15 totems total (offhand + 14 in inventory)
            }
            case MACE -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[0] = it(Material.MACE, 1, "density:5," + A);
                c[1] = it("NETHERITE_SWORD", 1, SWORD);
                c[2] = it(Material.WIND_CHARGE, 64);
                c[3] = it(Material.GOLDEN_APPLE, 64);
                c[4] = it(Material.ENDER_PEARL, 16);
                c[5] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1);
                c[7] = it(Material.SHIELD, 1, A);
                totems(c, 4); heals(c, 4);
            }
            case ELYTRA_MACE -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[38] = it(Material.ELYTRA, 1, A);
                c[13] = it("NETHERITE_CHESTPLATE", 1, P);
                c[0] = it(Material.MACE, 1, "density:5," + A);
                c[1] = it("NETHERITE_SWORD", 1, SWORD);
                c[2] = rockets(64);
                c[3] = it(Material.WIND_CHARGE, 64);
                c[4] = it(Material.GOLDEN_APPLE, 64);
                c[5] = it(Material.ENDER_PEARL, 16);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[7] = rockets(64);
                totems(c, 4); heals(c, 4);
            }
            case ROCKET_MACE -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[38] = it(Material.ELYTRA, 1, A);
                c[13] = it("NETHERITE_CHESTPLATE", 1, P);
                c[0] = it(Material.MACE, 1, "breach:4," + A);
                c[1] = it("NETHERITE_SWORD", 1, SWORD);
                c[2] = rockets(64);
                c[3] = rockets(64);
                c[4] = it(Material.GOLDEN_APPLE, 64);
                c[5] = it(Material.ENDER_PEARL, 16);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[7] = it(Material.WIND_CHARGE, 64);
                totems(c, 4); heals(c, 4);
            }
            case SPEAR_MACE -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[0] = it("NETHERITE_SPEAR", 1, "lunge:3," + A);
                c[1] = it(Material.MACE, 1, "density:5," + A);
                c[2] = it(Material.WIND_CHARGE, 64);
                c[3] = it(Material.GOLDEN_APPLE, 64);
                c[4] = it(Material.ENDER_PEARL, 16);
                c[5] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1);
                c[7] = it(Material.SHIELD, 1, A);
                totems(c, 4); heals(c, 4);
            }
            case SWORD -> {
                armor(c, "NETHERITE", P, "feather_falling:4,depth_strider:3");
                c[0] = it("NETHERITE_SWORD", 1, SWORD);
                c[1] = it(Material.BOW, 1, "power:5,punch:2,flame:1," + A);
                c[2] = it(Material.ARROW, 64);
                c[3] = it(Material.GOLDEN_APPLE, 64);
                c[4] = it(Material.ENDER_PEARL, 16);
                c[5] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[6] = potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1);
                c[7] = it(Material.SHIELD, 1, A);
                totems(c, 4); heals(c, 4);
            }
            case AXE -> {
                armor(c, "NETHERITE", P, "feather_falling:4");
                c[40] = it(Material.SHIELD, 1, A);
                c[0] = it("NETHERITE_AXE", 1, "sharpness:5,efficiency:5," + A);
                c[1] = it("NETHERITE_SWORD", 1, SWORD);
                c[2] = it(Material.GOLDEN_APPLE, 64);
                c[3] = it(Material.ENDER_PEARL, 16);
                c[4] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1);
                c[5] = potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1);
                c[6] = it(Material.COBWEB, 16);
                totems(c, 4); heals(c, 4);
            }
            case UHC -> {
                armor(c, "DIAMOND", "protection:3,unbreaking:3", "feather_falling:3");
                c[0] = it("DIAMOND_SWORD", 1, "sharpness:3,unbreaking:3");
                c[1] = it(Material.BOW, 1, "power:3,unbreaking:3");
                c[2] = it(Material.ARROW, 32);
                c[3] = it(Material.FISHING_ROD, 1);
                c[4] = it(Material.GOLDEN_APPLE, 12);
                c[5] = it(Material.COOKED_BEEF, 64);
                c[6] = it(Material.COBBLESTONE, 64);
                c[7] = it(Material.OAK_PLANKS, 64);
                c[8] = it(Material.ENDER_PEARL, 4);
                totems(c, 4);
            }
            case CREEPER -> {
                armor(c, "NETHERITE", "blast_protection:4," + A, "feather_falling:4");
                c[0] = it("NETHERITE_SWORD", 1, SWORD);
                c[1] = it(Material.CREEPER_SPAWN_EGG, 16);
                c[2] = it(Material.FLINT_AND_STEEL, 1);
                c[3] = it(Material.GOLDEN_APPLE, 64);
                c[4] = it(Material.ENDER_PEARL, 16);
                c[5] = it(Material.SHIELD, 1, A);
                c[6] = it(Material.BOW, 1, "power:5," + A);
                c[7] = it(Material.ARROW, 64);
                c[8] = it(Material.COBBLESTONE, 64);
                totems(c, 4); heals(c, 3);
            }
        }
        return c;
    }

    // ---------- Item library (everything the editor can add, per mode) ----------

    private static void add(List<ItemStack> l, Mode cur, ItemStack s, Mode... only) {
        if (s == null) return;
        if (only.length > 0) {
            boolean ok = false;
            for (Mode x : only) if (x == cur) ok = true;
            if (!ok) return;
        }
        l.add(s);
    }

    public static List<ItemStack> library(Mode m) {
        List<ItemStack> l = new ArrayList<>();
        Mode[] MACES = {Mode.MACE, Mode.ELYTRA_MACE, Mode.ROCKET_MACE, Mode.SPEAR_MACE};
        Mode[] FLY = {Mode.ELYTRA_MACE, Mode.ROCKET_MACE};

        // Swords
        for (String t : new String[]{"NETHERITE", "DIAMOND"}) {
            add(l, m, it(t + "_SWORD", 1, SWORD));
            add(l, m, it(t + "_SWORD", 1, "sharpness:5,knockback:2," + A));
            add(l, m, it(t + "_SWORD", 1, "sharpness:5," + A));
            add(l, m, it(t + "_AXE", 1, "sharpness:5,efficiency:5," + A));
        }
        // Maces
        add(l, m, it(Material.MACE, 1, "density:5," + A), MACES);
        add(l, m, it(Material.MACE, 1, "breach:4," + A), MACES);
        add(l, m, it(Material.MACE, 1, "wind_burst:3,density:5," + A), MACES);
        add(l, m, it(Material.MACE, 1, "wind_burst:3,breach:4," + A), MACES);
        // Spears
        for (String t : new String[]{"NETHERITE", "DIAMOND", "IRON"}) {
            for (int lv = 1; lv <= 3; lv++) add(l, m, it(t + "_SPEAR", 1, "lunge:" + lv + "," + A), Mode.SPEAR_MACE);
        }
        // Armor (netherite + diamond, each protection type)
        String[] prots = {"protection:4", "blast_protection:4", "projectile_protection:4", "fire_protection:4"};
        for (String t : new String[]{"NETHERITE", "DIAMOND"}) {
            for (String p : prots) {
                String base = p + "," + A;
                add(l, m, it(t + "_HELMET", 1, base + ",respiration:3,aqua_affinity:1"));
                add(l, m, it(t + "_CHESTPLATE", 1, base));
                add(l, m, it(t + "_LEGGINGS", 1, base + ",swift_sneak:3"));
                add(l, m, it(t + "_BOOTS", 1, base + ",feather_falling:4"));
            }
            add(l, m, it(t + "_CHESTPLATE", 1, "protection:4,thorns:3," + A));
            add(l, m, it(t + "_BOOTS", 1, "protection:4,feather_falling:4,depth_strider:3," + A));
            add(l, m, it(t + "_BOOTS", 1, "protection:4,feather_falling:4,soul_speed:3," + A));
        }
        add(l, m, it(Material.ELYTRA, 1, A), FLY);
        add(l, m, it(Material.SHIELD, 1, A));
        add(l, m, it(Material.TOTEM_OF_UNDYING, 1));
        // Crystal stuff
        add(l, m, it(Material.END_CRYSTAL, 64), Mode.CRYSTAL);
        add(l, m, it(Material.OBSIDIAN, 64), Mode.CRYSTAL);
        add(l, m, it(Material.RESPAWN_ANCHOR, 64), Mode.CRYSTAL);
        add(l, m, it(Material.GLOWSTONE, 64), Mode.CRYSTAL);
        // Mobility
        add(l, m, it(Material.WIND_CHARGE, 64), MACES);
        add(l, m, rockets(64), FLY);
        add(l, m, it(Material.ENDER_PEARL, 16));
        // Ranged
        add(l, m, it(Material.BOW, 1, "power:5,punch:2,flame:1," + A));
        add(l, m, it(Material.BOW, 1, "power:5," + A));
        add(l, m, it(Material.CROSSBOW, 1, "quick_charge:3,piercing:4," + A));
        add(l, m, it(Material.CROSSBOW, 1, "quick_charge:3,multishot:1," + A));
        add(l, m, it(Material.ARROW, 64));
        add(l, m, it(Material.TRIDENT, 1, "loyalty:3,impaling:5," + A));
        // Food / potions
        add(l, m, it(Material.GOLDEN_APPLE, 64));
        add(l, m, it(Material.ENCHANTED_GOLDEN_APPLE, 16));
        add(l, m, it(Material.COOKED_BEEF, 64));
        add(l, m, it(Material.GOLDEN_CARROT, 64));
        add(l, m, it(Material.EXPERIENCE_BOTTLE, 64));
        PotionType[] pts = {PotionType.STRONG_HEALING, PotionType.STRONG_SWIFTNESS, PotionType.STRONG_STRENGTH,
                PotionType.FIRE_RESISTANCE, PotionType.STRONG_REGENERATION, PotionType.SLOW_FALLING};
        for (PotionType pt : pts) add(l, m, potion(Material.SPLASH_POTION, pt, 1));
        for (PotionType pt : pts) add(l, m, potion(Material.POTION, pt, 1));
        // Mode specific
        add(l, m, it(Material.CREEPER_SPAWN_EGG, 16), Mode.CREEPER);
        add(l, m, it(Material.FLINT_AND_STEEL, 1), Mode.CREEPER);
        add(l, m, it(Material.COBBLESTONE, 64), Mode.UHC, Mode.CREEPER, Mode.CRYSTAL);
        add(l, m, it(Material.OAK_PLANKS, 64), Mode.UHC);
        add(l, m, it(Material.FISHING_ROD, 1), Mode.UHC);
        add(l, m, it(Material.COBWEB, 16), Mode.AXE, Mode.SWORD, Mode.UHC, Mode.CREEPER);
        return l;
    }
}

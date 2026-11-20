package dev.stormrider.naughtylist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;

/** Coal presents: a seed-fixed share of XmasWorld presents are traps that fire once. */
final class Presents implements Listener {
    private final NaughtyListPlugin p;
    private final Set<Location> fusing = new HashSet<>();
    private final NamespacedKey sprung;
    Presents(NaughtyListPlugin p) { this.p = p; sprung = p.key("sprung"); }

    /** XmasWorld loot kind of a present barrel, or null. Uses the 1.3 tag, else the classic present shape. */
    String kind(Block b) {
        if (b.getType() != Material.BARREL) return null;
        String k = p.hooks.containerType(b);
        if (k != null) return k.startsWith("present") ? k : null;
        if (!p.b("coal-presents.legacy-shape-detection", true)) return null;
        // XmasWorld gift box: barrel facing up on a wrapped block, surrounded by wrap. Trees, ornaments and lights
        // next to a present can replace a couple of its wrap blocks, so 6 of the 8 are enough.
        if (!(b.getBlockData() instanceof org.bukkit.block.data.Directional d) || d.getFacing() != BlockFace.UP) return null;
        if (!wrap(b.getRelative(0, -1, 0).getType())) return null;
        int wrapped = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if ((dx != 0 || dz != 0) && wrap(b.getRelative(dx, 0, dz).getType())) wrapped++;
        if (wrapped < 6) return null;
        if (b.getRelative(0, 1, 0).getType() == Material.WATER) return "present-under-ice";
        return covered(b) ? "present-cave" : "present";
    }
    /** true if real ground (not a tree, its ornaments, snow or ice) is above: a cave present */
    private static boolean covered(Block b) {
        int top = b.getWorld().getHighestBlockYAt(b.getLocation());
        for (int y = b.getY() + 1; y <= top; y++) {
            Material m = b.getWorld().getBlockAt(b.getX(), y, b.getZ()).getType();
            String n = m.name();
            if (!m.isSolid() || n.endsWith("_LEAVES") || n.endsWith("_LOG") || n.endsWith("_WOOD") || n.contains("GLASS") || n.contains("ICE")
                    || n.startsWith("SNOW") || n.endsWith("FROGLIGHT") || n.equals("SHROOMLIGHT") || n.equals("GLOWSTONE") || n.equals("SEA_LANTERN")
                    || n.endsWith("_WOOL") || n.endsWith("_CONCRETE") || n.endsWith("_PLANKS") || n.endsWith("GOLD_BLOCK")) continue;
            return true;
        }
        return false;
    }
    private static boolean wrap(Material m) { String n = m.name(); return n.endsWith("_CONCRETE") || n.endsWith("_WOOL"); }
    private double chance(String kind) {
        String k = switch (kind) { case "present-cave" -> "cave"; case "present-buried" -> "buried"; case "present-under-ice" -> "under-ice"; default -> "surface"; };
        return p.d("coal-presents.chance." + k, k.equals("surface") ? .10 : k.equals("under-ice") ? 0 : .15);
    }
    private long hash(Block b, long salt) {
        long h = b.getWorld().getSeed() * 0x9E3779B97F4A7C15L + salt;
        h ^= b.getX() * 0xC2B2AE3D27D4EB4FL; h ^= b.getY() * 0x165667B19E3779F9L; h ^= b.getZ() * 0x27D4EB2F165667C5L;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return h;
    }
    private double unit(Block b, long salt) { return (hash(b, salt) >>> 11) * 0x1.0p-53; }

    /** the trap outcome of this present, or null if it is a normal one (or already sprung) */
    String trap(Block b) {
        if (!p.b("coal-presents.enabled", true) || !p.world(b.getWorld())) return null;
        String kind = kind(b);
        if (kind == null || !(b.getState(false) instanceof TileState ts) || ts.getPersistentDataContainer().has(sprung)) return null;
        if (p.zones.safe(b.getLocation()) || unit(b, 7001) >= chance(kind)) return null;
        var o = p.getConfig().getConfigurationSection("coal-presents.outcomes");
        Map<String, Integer> w = new LinkedHashMap<>();
        for (String s : List.of("pop", "coal", "bugs", "sprites")) w.put(s, o == null ? 25 : Math.max(0, o.getInt(s, 0)));
        int total = w.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return null;
        double r = unit(b, 7002) * total;
        for (var e : w.entrySet()) { r -= e.getValue(); if (r < 0) return e.getKey(); }
        return "coal";
    }
    /** /naughty present: why this barrel is or isn't a coal present */
    List<String> explain(Block b) {
        List<String> out = new ArrayList<>();
        out.add("Barrel at " + b.getX() + " " + b.getY() + " " + b.getZ() + ":");
        String tag = p.hooks.containerType(b);
        String kind = kind(b);
        out.add(" XmasWorld loot tag: " + (tag == null ? "none (made before XmasWorld 1.3.0, or not an XmasWorld container)" : tag));
        out.add(" Present: " + (kind == null ? "no" + (tag == null && !p.b("coal-presents.legacy-shape-detection", true) ? " (legacy-shape-detection is off)" : tag == null ? " (not the gift-box shape: needs a barrel facing up on wool/concrete with at least 6 of its 8 sides wool/concrete)" : " (not a present table)") : kind + (tag == null ? " (found by its shape)" : "")));
        if (kind == null) return out;
        boolean sp = b.getState(false) instanceof TileState ts && ts.getPersistentDataContainer().has(sprung);
        boolean safe = p.zones.safe(b.getLocation());
        double roll = unit(b, 7001), ch = chance(kind);
        String t = trap(b);
        out.add(" Enabled: " + (p.b("coal-presents.enabled", true) && p.world(b.getWorld())) + ", safe zone: " + safe + ", already sprung: " + sp);
        out.add(" Roll " + String.format(Locale.ROOT, "%.3f", roll) + " vs chance " + ch + (roll < ch ? " (trap)" : " (normal)"));
        out.add(t != null ? " => TRAPPED: " + t : " => normal present" + (safe ? " (safe zones never have traps)" : sp ? " (already went off)" : ""));
        return out;
    }
    /** /naughty present scan: count presents in loaded chunks around a spot */
    List<String> scan(Location c, int radius) {
        int n = 0, tagged = 0, shaped = 0, trapped = 0, safe = 0, sprungN = 0, barrels = 0;
        Map<String, Integer> outcomes = new TreeMap<>();
        World w = c.getWorld();
        int cr = (radius >> 4) + 1, ccx = c.getBlockX() >> 4, ccz = c.getBlockZ() >> 4;
        for (int x = ccx - cr; x <= ccx + cr; x++) for (int z = ccz - cr; z <= ccz + cr; z++) {
            if (!w.isChunkLoaded(x, z)) continue;
            for (BlockState st : w.getChunkAt(x, z).getTileEntities()) {
                if (!(st instanceof Barrel) || st.getLocation().distanceSquared(c) > (double) radius * radius) continue;
                barrels++;
                Block b = st.getBlock();
                String kind = kind(b);
                if (p.b("debug", false)) p.log("scan barrel " + b.getX() + " " + b.getY() + " " + b.getZ() + " kind=" + kind + " tag=" + p.hooks.containerType(b) + " safe=" + p.zones.safe(b.getLocation()) + " trap=" + (kind == null ? null : trap(b)));
                if (kind == null) continue;
                n++;
                if (p.hooks.containerType(b) != null) tagged++; else shaped++;
                if (p.zones.safe(b.getLocation())) safe++;
                if (((TileState) st).getPersistentDataContainer().has(sprung)) sprungN++;
                String t = trap(b);
                if (t != null) { trapped++; outcomes.merge(t, 1, Integer::sum); }
            }
        }
        return List.of("Barrels within " + radius + " blocks (loaded chunks): " + barrels + "; presents: " + n + " (" + tagged + " tagged, " + shaped + " by shape)",
                "Trapped: " + trapped + " " + outcomes + "; in safe zones: " + safe + "; already sprung: " + sprungN);
    }
    private void markSprung(Block b) {
        if (b.getState(false) instanceof TileState ts) ts.getPersistentDataContainer().set(sprung, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Barrel barrel) || !(e.getPlayer() instanceof Player pl)) return;
        Block b = barrel.getBlock();
        if (fusing.contains(b.getLocation())) { e.setCancelled(true); return; }
        String t = trap(b);
        if (t == null) return;
        if (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR) {
            // admins can look inside: it opens normally and stays armed for survival players
            pl.sendActionBar(Component.text("Coal present (" + t + "): armed, but never set off in " + pl.getGameMode().name().toLowerCase(Locale.ROOT) + " mode", NamedTextColor.GOLD));
            return;
        }
        e.setCancelled(true);
        fuse(b, t, pl, false);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakIt(BlockBreakEvent e) {
        Block b = e.getBlock();
        String t = trap(b);
        if (t == null || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (t.equals("coal") && b.getState(false) instanceof Barrel barrel) coal(barrel.getInventory(), e.getPlayer());
        else fuse(b, t, e.getPlayer(), true);
        markSprung(b);
    }
    private void fuse(Block b, String outcome, Player pl, boolean broken) {
        Location c = b.getLocation().add(.5, .5, .5);
        fusing.add(b.getLocation());
        if (!broken) markSprung(b);
        b.getWorld().playSound(c, Sound.ENTITY_TNT_PRIMED, 1f, 1.4f);
        pl.sendActionBar(Component.text("This present is fizzing... step back!", NamedTextColor.RED));
        int ticks = (int) Math.round(20 * p.d("coal-presents.fuse-seconds", 1.5));
        for (int i = 0; i < ticks; i += 4) Bukkit.getScheduler().runTaskLater(p, () -> b.getWorld().spawnParticle(Particle.SMOKE, c.clone().add(0, .6, 0), 6, .3, .2, .3, .01), i);
        Bukkit.getScheduler().runTaskLater(p, () -> {
            fusing.remove(b.getLocation());
            p.log("Coal present at " + b.getX() + " " + b.getY() + " " + b.getZ() + " sprung: " + outcome + " (" + pl.getName() + ")");
            switch (outcome) {
                case "pop" -> pop(c);
                case "coal" -> { if (b.getState(false) instanceof Barrel br) coal(br.getInventory(), pl); }
                case "bugs" -> mobs(c, Silverfish.class, p.i("coal-presents.bugs.count", 3), "Coal Bug", 0);
                default -> mobs(c, Vex.class, p.i("coal-presents.sprites.count", 2), "Naughty Sprite", 20 * p.i("coal-presents.sprites.lifetime-seconds", 15));
            }
        }, ticks);
    }
    private void pop(Location c) {
        World w = c.getWorld();
        w.spawnParticle(Particle.EXPLOSION, c, 3, .4, .4, .4, 0);
        w.spawnParticle(Particle.SNOWFLAKE, c, 80, .6, .6, .6, .25);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
        double r = p.d("coal-presents.pop.radius", 3), dmg = p.d("coal-presents.pop.damage", 6), kb = p.d("coal-presents.pop.knockback", .8);
        for (Player pl : w.getPlayers()) {
            double d = pl.getLocation().add(0, .9, 0).distance(c);
            if (d >= r || !p.player(pl)) continue;
            double amount = dmg * (1 - d / r) * (pl.isBlocking() ? .5 : 1);
            if (amount > .1) pl.damage(amount, DamageSource.builder(DamageType.EXPLOSION).withDamageLocation(c).build());
            Vector v = pl.getLocation().toVector().subtract(c.toVector());
            if (v.lengthSquared() < .01) v = new Vector(0, 1, 0);
            pl.setVelocity(pl.getVelocity().add(v.normalize().multiply(kb).setY(.4)));
        }
    }
    private void coal(Inventory inv, Player pl) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : inv.getContents()) if (it != null && !it.getType().isAir()) items.add(it);
        inv.clear();
        Random r = new Random();
        int min = p.i("coal-presents.coal.min", 3), max = Math.max(min, p.i("coal-presents.coal.max", 8));
        inv.addItem(new ItemStack(Material.COAL, min + r.nextInt(max - min + 1)));
        if (p.b("coal-presents.coal.keep-one-item", true) && !items.isEmpty()) inv.addItem(items.get(r.nextInt(items.size())));
        pl.sendMessage(Component.text("Lumps of coal! Somebody's been naughty...", NamedTextColor.DARK_GRAY));
    }
    private <T extends Mob> void mobs(Location c, Class<T> cls, int n, String name, int life) {
        for (int i = 0; i < n; i++) {
            if (p.hooks.spawnDenied(c)) return;
            c.getWorld().spawn(c.clone().add(0, .6, 0), cls, CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
                p.tag(m, cls == Vex.class ? "sprite" : "bug");
                p.name(m, name, false);
                if (m instanceof Vex v) { v.getEquipment().setItemInMainHand(null); v.setLimitedLifetimeTicks(life); }
            });
        }
    }
}

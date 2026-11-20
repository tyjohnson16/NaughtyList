package dev.stormrider.naughtylist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Night snowstorms: frosty screens outdoors, more monsters, shallow water freezes. */
final class Blizzard implements Listener {
    private static final class State { boolean active; int warnUntil = -1, startedAt, spawnLimit = -1; boolean rolledTonight, manual; }
    private final NaughtyListPlugin p;
    private final Map<UUID, State> states = new HashMap<>();
    private final Random rnd = new Random();
    private int clock;
    Blizzard(NaughtyListPlugin p) { this.p = p; }
    private State st(World w) { return states.computeIfAbsent(w.getUID(), k -> new State()); }
    boolean active(World w) { State s = states.get(w.getUID()); return s != null && s.active; }

    void warn(World w) {
        State s = st(w);
        if (s.active || s.warnUntil >= 0) return;
        s.warnUntil = clock + 20 * p.i("blizzards.warning-seconds", 30);
        for (Player pl : w.getPlayers()) {
            pl.showTitle(Title.title(Component.text("❄ Blizzard coming! ❄", NamedTextColor.AQUA), Component.text("Find shelter, a roof or warm leather clothes", NamedTextColor.WHITE)));
            pl.sendMessage(Component.text("A blizzard is rolling in! Find shelter!", NamedTextColor.AQUA));
        }
    }
    void start(World w) { start(w, false); }
    /** manual = started by /naughty blizzard start: keeps the current time and also runs in daytime, until the next sunrise (or stop) */
    void start(World w, boolean manual) {
        State s = st(w);
        s.warnUntil = -1;
        if (s.active) return;
        s.active = true; s.manual = manual; s.startedAt = clock; s.rolledTonight = true;
        if (!w.hasStorm()) w.setStorm(true);
        long left = manual ? Math.floorMod(23000 - w.getTime() % 24000, 24000) : 23000 - (w.getTime() % 24000);
        w.setWeatherDuration((int) Math.max(1200, left + 200));
        s.spawnLimit = w.getSpawnLimit(SpawnCategory.MONSTER);
        w.setSpawnLimit(SpawnCategory.MONSTER, (int) Math.round(s.spawnLimit * p.d("blizzards.monster-cap-multiplier", 1.5)));
        for (Player pl : w.getPlayers()) pl.sendMessage(Component.text("❄ The blizzard is here! ❄", NamedTextColor.AQUA));
        p.log("Blizzard started in " + w.getName());
    }
    /** ends our blizzard state (keeps vanilla weather unless clear=true) */
    void end(World w, boolean clear) {
        State s = st(w);
        s.warnUntil = -1;
        if (!s.active) return;
        s.active = false; s.manual = false;
        if (s.spawnLimit >= 0) w.setSpawnLimit(SpawnCategory.MONSTER, s.spawnLimit);
        s.spawnLimit = -1;
        if (clear && w.hasStorm()) { w.setStorm(false); w.setThundering(false); }
        for (Player pl : w.getPlayers()) pl.sendMessage(Component.text("The blizzard is over. Phew!", NamedTextColor.AQUA));
        p.log("Blizzard ended in " + w.getName());
    }
    void stop(World w) { end(w, true); }
    void close() { for (World w : Bukkit.getWorlds()) if (active(w)) end(w, false); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void weather(WeatherChangeEvent e) { if (!e.toWeatherState() && active(e.getWorld())) end(e.getWorld(), false); }

    void tick(int c) {
        clock = c;
        if (!p.b("blizzards.enabled", true)) return;
        for (World w : Bukkit.getWorlds()) {
            if (!p.world(w)) continue;
            State s = st(w);
            boolean night = p.night(w);
            if (!night) {
                s.rolledTonight = false; s.warnUntil = -1;
                if (s.active && !s.manual) end(w, true);
                if (!s.active) continue;
            } else if (!s.rolledTonight && !s.active && s.warnUntil < 0) {
                s.rolledTonight = true;
                if (w.isThundering() && p.b("blizzards.during-thunderstorms", true) || rnd.nextDouble() < p.d("blizzards.chance-per-night", 0.2)) warn(w);
            }
            if (s.warnUntil >= 0 && clock >= s.warnUntil) start(w);
            if (!s.active) continue;
            if (!w.hasStorm()) { end(w, false); continue; }
            int max = p.i("blizzards.max-minutes", 0);
            if (max > 0 && clock - s.startedAt > max * 1200) { end(w, true); continue; }
            for (Player pl : w.getPlayers()) effects(pl);
        }
    }
    boolean outdoors(Player pl) {
        Location l = pl.getEyeLocation();
        return l.getBlockY() >= l.getWorld().getHighestBlockYAt(l, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }
    private void effects(Player pl) {
        if (!p.player(pl) || p.zones.safe(pl.getLocation()) || !outdoors(pl)) return;
        int leather = 0;
        for (ItemStack it : pl.getInventory().getArmorContents()) if (it != null && it.getType().name().startsWith("LEATHER_")) leather++;
        int frost = (int) Math.round(Math.min(130, p.i("blizzards.frost-overlay", 110)) * Math.max(0, 1 - leather * p.d("blizzards.leather-reduces-frost", .25)));
        if (frost > pl.getFreezeTicks()) pl.setFreezeTicks(Math.min(139, frost));
        Location l = pl.getLocation().add(0, 1.5, 0);
        pl.spawnParticle(Particle.SNOWFLAKE, l, 40, 5, 2.5, 5, .08);
        pl.spawnParticle(Particle.WHITE_ASH, l, 30, 5, 2.5, 5, .02);
        if (clock % 120 == 0) pl.playSound(pl.getLocation(), Sound.ITEM_ELYTRA_FLYING, .25f, .5f);
        if (p.b("blizzards.freeze-water.enabled", true) && clock % (20 * Math.max(1, p.i("blizzards.freeze-water.every-seconds", 2))) == 0) freeze(pl);
    }
    private void freeze(Player pl) {
        int r = p.i("blizzards.freeze-water.radius", 24), n = p.i("blizzards.freeze-water.blocks-per-player", 6);
        World w = pl.getWorld();
        for (int i = 0; i < n * 3 && n > 0; i++) {
            Block top = w.getHighestBlockAt(pl.getLocation().getBlockX() + rnd.nextInt(2 * r + 1) - r, pl.getLocation().getBlockZ() + rnd.nextInt(2 * r + 1) - r);
            if (top.getType() != Material.WATER || !(top.getBlockData() instanceof Levelled lv) || lv.getLevel() != 0) continue;
            if (top.getRelative(0, -1, 0).getType() == Material.WATER) continue; // only 1 deep
            if (near(top, Material.FARMLAND, 2) || !w.getNearbyEntities(top.getLocation().add(.5, .5, .5), 2.5, 2.5, 2.5).isEmpty()) continue;
            Location l = top.getLocation();
            if (p.hooks.claim(l) || p.hooks.anyRegion(l) || p.zones.safe(l)) continue;
            top.setType(Material.ICE);
            if (--n <= 0) return;
        }
    }
    private static boolean near(Block b, Material m, int r) {
        for (int x = -r; x <= r; x++) for (int y = -1; y <= 1; y++) for (int z = -r; z <= r; z++) if (b.getRelative(x, y, z).getType() == m) return true;
        return false;
    }
}

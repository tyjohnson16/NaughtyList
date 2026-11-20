package dev.stormrider.naughtylist;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.*;

/** Optional integrations. Each nested class is only touched when its plugin is enabled. */
final class Hooks {
    record Struct(String type, int x, int y, int z, int radius, boolean underground) {}
    final boolean xmas, wg, gp;
    private final NaughtyListPlugin plugin;
    Hooks(NaughtyListPlugin plugin) {
        this.plugin = plugin;
        boolean x = false;
        if (Bukkit.getPluginManager().isPluginEnabled("XmasWorld")) {
            try { Class.forName("dev.stormrider.xmasworld.XmasWorldAPI"); x = true; }
            catch (ClassNotFoundException e) { plugin.getLogger().warning("XmasWorld is older than 1.3.0: safe zones only cover spawn/regions, nutcrackers are off. Update XmasWorld to 1.3.0."); }
        }
        xmas = x;
        wg = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        gp = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
    }
    boolean christmas(World w) {
        if (xmas) return XW.christmas(w);
        return w.getGenerator() != null && w.getGenerator().getClass().getName().startsWith("dev.stormrider.xmasworld");
    }
    List<Struct> structures(World w, int x, int z, int r) { return xmas ? XW.near(w, x, z, r) : List.of(); }
    String containerType(Block b) { return xmas ? XW.container(b) : null; }
    boolean inRegion(Location l, Collection<String> names) { return wg && !names.isEmpty() && WG.in(l, names); }
    boolean spawnDenied(Location l) { return wg && WG.spawnDenied(l); }
    boolean anyRegion(Location l) { return wg && WG.any(l); }
    boolean claim(Location l) { return gp && GP.claim(l) != null; }
    boolean ownClaim(Player p, Location l) { return gp && GP.owner(l, p.getUniqueId()); }

    static final class XW {
        static boolean christmas(World w) { return dev.stormrider.xmasworld.XmasWorldAPI.isChristmasWorld(w); }
        static List<Struct> near(World w, int x, int z, int r) {
            List<Struct> out = new ArrayList<>();
            for (var s : dev.stormrider.xmasworld.XmasWorldAPI.structuresNear(w, x, z, r)) out.add(new Struct(s.type(), s.x(), s.y(), s.z(), s.radius(), s.underground()));
            return out;
        }
        static String container(Block b) { return dev.stormrider.xmasworld.XmasWorldAPI.containerType(b); }
    }
    static final class WG {
        static com.sk89q.worldguard.protection.ApplicableRegionSet set(Location l) {
            return com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(l));
        }
        static boolean in(Location l, Collection<String> names) {
            for (var r : set(l)) if (names.contains(r.getId())) return true;
            return false;
        }
        static boolean any(Location l) { return set(l).size() > 0; }
        static boolean spawnDenied(Location l) {
            return set(l).queryState(null, com.sk89q.worldguard.protection.flags.Flags.MOB_SPAWNING) == com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
        }
    }
    static final class GP {
        static me.ryanhamshire.GriefPrevention.Claim claim(Location l) {
            var gp = me.ryanhamshire.GriefPrevention.GriefPrevention.instance;
            return gp == null ? null : gp.dataStore.getClaimAt(l, true, null);
        }
        static boolean owner(Location l, UUID id) {
            var c = claim(l);
            return c != null && id.equals(c.getOwnerID());
        }
    }
}

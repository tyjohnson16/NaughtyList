package dev.stormrider.naughtylist;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

/** Cosy places: listed XmasWorld builds (2D footprint + padding), world spawn, optional WG regions / GP claims. */
final class SafeZones {
    private record Rect(int x0, int z0, int x1, int z1) {}
    private final NaughtyListPlugin p;
    private final Map<String, List<Rect>> cache = new LinkedHashMap<>(256, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, List<Rect>> e) { return size() > 4096; }
    };
    SafeZones(NaughtyListPlugin p) { this.p = p; }
    void clear() { cache.clear(); }

    boolean safe(Location l) {
        World w = l.getWorld();
        if (w == null || !p.world(w)) return false;
        int sr = p.i("safe-zones.spawn-radius", 32);
        Location sp = w.getSpawnLocation();
        if (sr > 0 && Math.abs(l.getBlockX() - sp.getBlockX()) <= sr && Math.abs(l.getBlockZ() - sp.getBlockZ()) <= sr) return true;
        for (Rect r : rects(w, l.getBlockX() >> 4, l.getBlockZ() >> 4))
            if (l.getBlockX() >= r.x0 && l.getBlockX() <= r.x1 && l.getBlockZ() >= r.z0 && l.getBlockZ() <= r.z1) return true;
        if (p.hooks.inRegion(l, p.getConfig().getStringList("safe-zones.worldguard-regions"))) return true;
        return p.b("safe-zones.griefprevention-claims", false) && p.hooks.claim(l);
    }
    private List<Rect> rects(World w, int cx, int cz) {
        String k = w.getUID() + ":" + cx + ":" + cz;
        List<Rect> r = cache.get(k);
        if (r != null) return r;
        int pad = p.i("safe-zones.padding", 6);
        Set<String> types = new HashSet<>(p.getConfig().getStringList("safe-zones.structures"));
        r = new ArrayList<>();
        for (var s : p.hooks.structures(w, (cx << 4) + 8, (cz << 4) + 8, 8 + pad))
            if (types.contains(s.type())) r.add(new Rect(s.x() - s.radius() - pad, s.z() - s.radius() - pad, s.x() + s.radius() + pad, s.z() + s.radius() + pad));
        cache.put(k, r);
        return r;
    }
}

package dev.stormrider.naughtylist;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Nutcracker guard posts at XmasWorld's bigger loot spots: garrison once, patrol, refill after N minutes. */
final class Posts implements Listener {
    final class Post {
        final World w; final String type; final int x, y, z, radius; final boolean underground; final String id;
        Post(World w, Hooks.Struct s) { this.w = w; type = s.type(); x = s.x(); y = s.y(); z = s.z(); radius = s.radius(); underground = s.underground(); id = type + "@" + x + "," + z; }
        void tag(NaughtyListPlugin p, Entity e) {
            e.getPersistentDataContainer().set(p.key("post"), PersistentDataType.STRING, id);
            e.getPersistentDataContainer().set(p.key("home"), PersistentDataType.INTEGER_ARRAY, new int[]{x, y, z, radius});
        }
        Chunk chunk() { return w.getChunkAt(x >> 4, z >> 4); }
        int size() { return p.i("nutcrackers.posts." + type, 0); }
    }
    private final NaughtyListPlugin p;
    private final Map<String, Post> loaded = new HashMap<>();
    private final Random rnd = new Random();
    private final NamespacedKey last;
    Posts(NaughtyListPlugin p) {
        this.p = p; last = p.key("garrisoned");
        Bukkit.getScheduler().runTask(p, () -> { for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) scan(c); });
    }
    int count(World w) { return (int) loaded.values().stream().filter(x -> x.w == w).count(); }
    void save() {}

    @EventHandler public void load(EntitiesLoadEvent e) { scan(e.getChunk()); }
    private void scan(Chunk c) {
        World w = c.getWorld();
        if (!p.b("nutcrackers.enabled", true) || !p.hooks.xmas || !p.world(w)) return;
        for (Hooks.Struct s : p.hooks.structures(w, (c.getX() << 4) + 8, (c.getZ() << 4) + 8, 8)) {
            if (s.x() >> 4 != c.getX() || s.z() >> 4 != c.getZ()) continue;
            Post post = new Post(w, s);
            if (post.size() <= 0 || p.zones.safe(new Location(w, s.x(), s.y(), s.z()))) continue;
            loaded.put(post.id, post);
        }
    }
    private List<Skeleton> guards(Post post) {
        List<Skeleton> out = new ArrayList<>();
        int r = post.radius + p.i("nutcrackers.leash", 24) + 16;
        for (Entity e : post.w.getNearbyEntities(new Location(post.w, post.x, post.y, post.z), r, 128, r))
            if (e instanceof Skeleton s && !s.isDead() && post.id.equals(s.getPersistentDataContainer().get(p.key("post"), PersistentDataType.STRING))) out.add(s);
        return out;
    }
    /** chunk PDC: time (ms) the post was last filled up */
    private long lastFill(Post post) { Long v = post.chunk().getPersistentDataContainer().get(new NamespacedKey(p, "post_" + post.id.replaceAll("[^a-z0-9_.-]", "_")), PersistentDataType.LONG); return v == null ? 0 : v; }
    private void setFill(Post post) { post.chunk().getPersistentDataContainer().set(new NamespacedKey(p, "post_" + post.id.replaceAll("[^a-z0-9_.-]", "_")), PersistentDataType.LONG, System.currentTimeMillis()); }

    void tick(int clock) {
        if (!p.b("nutcrackers.enabled", true)) return;
        if (clock % 40 == 0) patrol();
        if (clock % 100 != 0) return;
        Iterator<Post> it = loaded.values().iterator();
        while (it.hasNext()) {
            Post post = it.next();
            if (!post.w.isChunkLoaded(post.x >> 4, post.z >> 4)) { it.remove(); continue; }
            if (!post.w.isChunkLoaded((post.x >> 4) + 1, post.z >> 4) || !post.w.isChunkLoaded((post.x >> 4) - 1, post.z >> 4)) continue;
            List<Skeleton> g = guards(post);
            if (g.size() >= post.size()) continue;
            long lf = lastFill(post);
            double mins = p.d("nutcrackers.respawn-minutes", 60);
            if (lf != 0 && (mins <= 0 || System.currentTimeMillis() - lf < mins * 60_000)) continue;
            if (p.threats.nearest(new Location(post.w, post.x, post.y, post.z), p.d("nutcrackers.respawn-player-distance", 32)) != null) continue;
            garrison(post, g);
        }
    }
    private void garrison(Post post, List<Skeleton> existing) {
        boolean wantCaptain = p.getConfig().getStringList("nutcrackers.captain-at").contains(post.type) && existing.stream().noneMatch(s -> "captain".equals(p.type(s)));
        int need = post.size() - (int) existing.stream().filter(s -> "guard".equals(p.type(s))).count();
        int made = 0;
        for (int i = 0; i < need + (wantCaptain ? 1 : 0); i++) {
            Location l = spot(post);
            if (l == null) continue;
            if (p.threats.guard(l, post, wantCaptain && i == need) != null) made++;
        }
        setFill(post);
        p.log("Nutcracker post " + post.id + " (" + post.w.getName() + "): spawned " + made + " (had " + existing.size() + ")");
    }
    private Location spot(Post post) {
        for (int tries = 0; tries < 20; tries++) {
            int r = Math.max(2, post.radius / 2);
            int x = post.x + rnd.nextInt(2 * r + 1) - r, z = post.z + rnd.nextInt(2 * r + 1) - r;
            if (post.underground) {
                for (int y = post.y; y <= post.y + 4; y++) {
                    Block b = post.w.getBlockAt(x, y, z);
                    if (b.isPassable() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, -1, 0).getType().isSolid()) return b.getLocation().add(.5, 0, .5);
                }
            } else {
                Block top = post.w.getHighestBlockAt(x, z);
                if (top.getType().isSolid() && !top.isLiquid()) return top.getLocation().add(.5, 1, .5);
            }
        }
        return null;
    }
    /** wander around the post; drop a chase that pulls them more than `leash` blocks from home */
    private void patrol() {
        int leash = p.i("nutcrackers.leash", 24);
        for (World w : Bukkit.getWorlds()) {
            if (!p.world(w)) continue;
            for (Skeleton s : w.getEntitiesByClass(Skeleton.class)) {
                int[] h = s.getPersistentDataContainer().get(p.key("home"), PersistentDataType.INTEGER_ARRAY);
                if (h == null || h.length < 4) continue;
                Location home = new Location(w, h[0] + .5, h[1], h[2] + .5);
                double d = Math.hypot(s.getLocation().getX() - home.getX(), s.getLocation().getZ() - home.getZ());
                if (s.getTarget() != null) {
                    if (d > leash || !(s.getTarget() instanceof Player pl) || !p.canHurt(pl)) { s.setTarget(null); s.getPathfinder().moveTo(home, 1.1); }
                    continue;
                }
                if (d > h[3]) { s.getPathfinder().moveTo(home, 1.0); continue; }
                if (rnd.nextDouble() < .3) {
                    int x = h[0] + rnd.nextInt(2 * h[3] + 1) - h[3], z = h[2] + rnd.nextInt(2 * h[3] + 1) - h[3];
                    int y = w.getHighestBlockYAt(x, z) + 1;
                    if (Math.abs(y - s.getLocation().getY()) < 6) s.getPathfinder().moveTo(new Location(w, x + .5, y, z + .5), .8);
                }
            }
        }
    }
}

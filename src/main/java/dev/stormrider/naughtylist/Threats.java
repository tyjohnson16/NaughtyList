package dev.stormrider.naughtylist;

import com.destroystokyo.paper.entity.ai.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Spawning, looks and combat for evil snowmen, the Abominable Snowman, goblins and nutcrackers. */
final class Threats implements Listener {
    static final Set<EntityType> REPLACEABLE = EnumSet.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.STRAY);
    private final NaughtyListPlugin p;
    private final Random rnd = new Random();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> cool = new HashMap<>();
    Threats(NaughtyListPlugin p) { this.p = p; for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) restore(e); }

    Entity spawn(String type, Location l) {
        return switch (type) {
            case "snowman" -> snowman(l, false, true);
            case "abominable" -> snowman(l, true, true);
            case "goblin" -> p.goblins.spawn(l);
            case "guard" -> guard(l, null, false);
            case "captain" -> guard(l, null, true);
            default -> null;
        };
    }

    Snowman snowman(Location l, boolean big) { return snowman(l, big, false); }
    /** manual = spawned by /naughty spawn: doesn't melt in daylight, and only melts when no player at all is within 96 blocks */
    Snowman snowman(Location l, boolean big, boolean manual) {
        if (p.protectedAt(l)) return null;
        Snowman sm = l.getWorld().spawn(l, Snowman.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            p.tag(s, big ? "abominable" : "snowman");
            if (manual) s.getPersistentDataContainer().set(p.key("manual"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            s.setDerp(true);
            s.setRemoveWhenFarAway(true);
            String pre = big ? "evil-snowmen.abominable." : "evil-snowmen.";
            attr(s, Attribute.MAX_HEALTH, p.d(pre + "health", big ? 80 : 16));
            s.setHealth(p.d(pre + "health", big ? 80 : 16));
            if (big) { attr(s, Attribute.SCALE, p.d(pre + "scale", 2.5)); attr(s, Attribute.MOVEMENT_SPEED, 0.2); }
            attr(s, Attribute.FOLLOW_RANGE, big ? 24 : 16);
            p.name(s, big ? "The Abominable Snowman" : "Evil Snowman", big);
            ai(s);
        });
        if (big) Bukkit.getScheduler().runTask(p, () -> { if (sm.isValid()) scale(sm); }); // re-apply after it's in the world
        return sm;
    }
    /** keep the Abominable giant even if something reset its scale */
    void scale(Snowman s) {
        var a = s.getAttribute(Attribute.SCALE);
        double want = p.d("evil-snowmen.abominable.scale", 2.5);
        if (a != null && a.getBaseValue() != want) a.setBaseValue(want);
    }

    Skeleton guard(Location l, Posts.Post post, boolean captain) {
        if (p.protectedAt(l)) return null;
        return l.getWorld().spawn(l, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            p.tag(s, captain ? "captain" : "guard");
            s.setShouldBurnInDay(false); s.setRemoveWhenFarAway(false); s.setPersistent(true); s.setCanPickupItems(false);
            double hp = p.d(captain ? "nutcrackers.captain.health" : "nutcrackers.guard.health", captain ? 45 : 30);
            attr(s, Attribute.MAX_HEALTH, hp); s.setHealth(hp);
            var eq = s.getEquipment();
            eq.setHelmet(captain ? unbreakable(new ItemStack(Material.GOLDEN_HELMET)) : leather(Material.LEATHER_HELMET, "cap", "#1D1D21"));
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, "coat", "#B02E26"));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, "trousers", "#F9FFFE"));
            eq.setBoots(leather(Material.LEATHER_BOOTS, "boots", "#1D1D21"));
            ItemStack bow = new ItemStack(Material.BOW);
            if (captain) {
                bow.addUnsafeEnchantment(Enchantment.POWER, p.i("nutcrackers.captain.power", 2));
                bow.addUnsafeEnchantment(Enchantment.PUNCH, p.i("nutcrackers.captain.punch", 1));
            }
            eq.setItemInMainHand(bow);
            eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setLeggingsDropChance(0); eq.setBootsDropChance(0); eq.setItemInMainHandDropChance(0);
            p.name(s, captain ? "Nutcracker Captain" : "Nutcracker Guard", captain);
            if (post != null) post.tag(p, s);
        });
    }

    private ItemStack leather(Material m, String part, String def) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        String hex = p.getConfig().getString("nutcrackers.guard." + part, def);
        try { meta.setColor(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16))); } catch (RuntimeException ignored) {}
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }
    static ItemStack unbreakable(ItemStack it) { var m = it.getItemMeta(); m.setUnbreakable(true); it.setItemMeta(m); return it; }
    static void attr(LivingEntity e, Attribute a, double v) { var i = e.getAttribute(a); if (i != null) i.setBaseValue(v); }

    /** Paper goals don't survive restarts/chunk unloads: re-apply whenever our mobs load. */
    void restore(Entity e) {
        String t = p.type(e);
        if (t == null) return;
        if (e instanceof Snowman s) ai(s);
        if (e instanceof Zombie z && t.equals("goblin")) p.goblins.restore(z);
    }
    @EventHandler public void load(EntitiesLoadEvent e) { for (Entity x : e.getEntities()) restore(x); }

    private void ai(Snowman s) {
        MobGoals goals = Bukkit.getMobGoals();
        goals.removeAllGoals(s, GoalType.TARGET);
        goals.addGoal(s, 1, new TargetPlayer(s));
    }
    /** target the nearest valid player (not in a safe zone) within follow range */
    private final class TargetPlayer implements Goal<Snowman> {
        private final Snowman s; private final GoalKey<Snowman> key = GoalKey.of(Snowman.class, new NamespacedKey(p, "target_player"));
        private int calls;
        TargetPlayer(Snowman s) { this.s = s; }
        public boolean shouldActivate() {
            if (++calls % 5 != 0) return false; // goal selectors run every 1-2 ticks: re-scan ~4x a second
            Player best = null; double bd = Math.pow(s.getAttribute(Attribute.FOLLOW_RANGE).getValue(), 2);
            for (Player pl : s.getWorld().getPlayers()) {
                double d = pl.getLocation().distanceSquared(s.getLocation());
                if (d < bd && p.canHurt(pl) && s.hasLineOfSight(pl)) { bd = d; best = pl; }
            }
            if (best != null) s.setTarget(best);
            return false;
        }
        public GoalKey<Snowman> getKey() { return key; }
        public EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.TARGET); }
    }

    // --- natural spawn replacement -----------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void natural(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Location l = e.getLocation();
        World w = l.getWorld();
        if (!p.world(w)) return;
        if (e.getEntity() instanceof Monster && p.zones.safe(l) && p.b("safe-zones.block-vanilla-monster-spawns", true)) { e.setCancelled(true); return; }
        if (!REPLACEABLE.contains(e.getEntityType()) || !p.night(w) || l.getBlockY() < w.getHighestBlockYAt(l) || p.protectedAt(l)) return;
        if (l.getBlock().isLiquid() || l.getBlock().getRelative(0, -1, 0).isLiquid()) return; // no snowmen/goblins in water
        if (w.getLivingEntities().stream().filter(x -> p.type(x) != null).count() >= p.i("max-mobs-per-world", 80)) return;
        Player near = nearest(l, 48);
        if (near == null) return;
        double r = rnd.nextDouble();
        double gob = p.b("grinch-goblins.enabled", true) ? p.d("grinch-goblins.replace-chance", 0.03) : 0;
        double sm = p.b("evil-snowmen.enabled", true) ? p.d("evil-snowmen.replace-chance", 0.12) * (p.blizzard.active(w) ? p.d("evil-snowmen.blizzard-multiplier", 2) : 1) : 0;
        if (r < gob) {
            if (count(near, "goblin") >= p.i("grinch-goblins.cap-per-player", 1) || !p.goblins.canTarget(near)) return;
            e.setCancelled(true);
            p.goblins.spawn(l);
        } else if (r < gob + sm) {
            boolean big = p.blizzard.active(w) && p.b("evil-snowmen.abominable.enabled", true) && rnd.nextDouble() < p.d("evil-snowmen.abominable.chance", 0.0167) && noAbominableNear(l);
            if (!big && count(near, "snowman") >= p.i("evil-snowmen.cap-per-player", 4)) return;
            e.setCancelled(true);
            snowman(l, big);
        }
    }
    private boolean noAbominableNear(Location l) {
        double r = p.d("evil-snowmen.abominable.min-distance", 256);
        for (Entity x : l.getWorld().getLivingEntities()) if ("abominable".equals(p.type(x)) && x.getLocation().distanceSquared(l) < r * r) return false;
        return true;
    }
    Player nearest(Location l, double r) {
        Player best = null; double bd = r * r;
        for (Player pl : l.getWorld().getPlayers()) { double d = pl.getLocation().distanceSquared(l); if (d < bd && p.player(pl)) { bd = d; best = pl; } }
        return best;
    }
    private boolean anyPlayerNear(Location l, double r) {
        for (Player pl : l.getWorld().getPlayers()) if (pl.getLocation().distanceSquared(l) < r * r) return true;
        return false;
    }
    int count(Player pl, String type) {
        int n = 0;
        for (Entity x : pl.getNearbyEntities(48, 48, 48)) if (type.equals(p.type(x))) n++;
        return n;
    }

    // --- combat ------------------------------------------------------------------------------
    @EventHandler public void launch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Snowball b && b.getShooter() instanceof Snowman s && p.type(s) != null) b.setItem(new ItemStack(Material.ICE));
    }
    private final Map<UUID, Integer> lastShard = new HashMap<>();
    @EventHandler public void hit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball b) || !(b.getShooter() instanceof Snowman s) || p.type(s) == null) return;
        if (!(e.getHitEntity() instanceof Player pl) || !p.canHurt(pl) || pl.isBlocking()) return;
        boolean big = "abominable".equals(p.type(s));
        String pre = big ? "evil-snowmen.abominable.volley." : "evil-snowmen.shard.";
        int now = Bukkit.getCurrentTick();
        Integer last = lastShard.put(pl.getUniqueId(), now);
        if (last == null || now - last >= 20) p.hurt(pl, p.d(pre + "damage", big ? 3 : 2), s, b, DamageType.MOB_PROJECTILE); // max one shard hit per second
        pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * p.i(pre + "slowness-seconds", big ? 3 : 2), Math.max(0, p.i(pre + "slowness-level", big ? 3 : 2) - 1)));
        pl.setFreezeTicks(Math.min(139, Math.max(pl.getFreezeTicks(), p.i("evil-snowmen.shard.frost-ticks", 60))));
        pl.getWorld().spawnParticle(Particle.SNOWFLAKE, pl.getLocation().add(0, 1, 0), 12, .3, .5, .3, .02);
    }
    /** our threats never go after players standing in a safe zone; optional for vanilla monsters too */
    @EventHandler(ignoreCancelled = true) public void target(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player pl) || !p.world(pl.getWorld())) return;
        boolean ours = p.type(e.getEntity()) != null;
        if ((ours || p.b("safe-zones.clear-vanilla-targets", false) && e.getEntity() instanceof Monster) && !p.canHurt(pl)) e.setCancelled(true);
        if (ours && "goblin".equals(p.type(e.getEntity())) && !p.goblins.canTarget(pl)) e.setCancelled(true);
    }
    /** our snowmen only melt by daylight / safe zones (handled in tick), not from rain, water or warm biomes */
    @EventHandler(ignoreCancelled = true) public void selfHarm(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Snowman) || p.type(e.getEntity()) == null) return;
        var c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.DROWNING || c == EntityDamageEvent.DamageCause.MELTING) e.setCancelled(true);
    }
    @EventHandler public void death(EntityDeathEvent e) {
        String t = p.type(e.getEntity());
        if (t == null) return;
        Player k = e.getEntity().getKiller();
        switch (t) {
            case "snowman" -> { e.getDrops().clear(); e.getDrops().add(new ItemStack(Material.SNOWBALL, 2 + rnd.nextInt(3))); }
            case "abominable" -> {
                e.getDrops().clear();
                e.getDrops().add(new ItemStack(Material.SNOW_BLOCK, 16)); e.getDrops().add(new ItemStack(Material.PACKED_ICE, 8));
                ItemStack nose = new ItemStack(Material.GOLDEN_CARROT);
                nose.editMeta(m -> m.customName(Component.text("Abominable's Carrot Nose", net.kyori.adventure.text.format.NamedTextColor.GOLD)));
                e.getDrops().add(nose); e.setDroppedExp(50);
                BossBar bar = bars.remove(e.getEntity().getUniqueId());
                if (bar != null) for (Player pl : Bukkit.getOnlinePlayers()) pl.hideBossBar(bar);
                p.reward("abominable-kill", k);
            }
            case "captain" -> {
                ItemStack medal = new ItemStack(Material.GOLD_NUGGET);
                medal.editMeta(m -> m.customName(Component.text("Captain's Medal", net.kyori.adventure.text.format.NamedTextColor.GOLD)));
                e.getDrops().add(medal); e.setDroppedExp(25);
                p.reward("captain-kill", k);
            }
            default -> {}
        }
    }

    // --- every 10 ticks ----------------------------------------------------------------------
    void tick(int clock) {
        for (World w : Bukkit.getWorlds()) {
            if (!p.world(w)) continue;
            boolean day = !p.night(w);
            for (Snowman s : w.getEntitiesByClass(Snowman.class)) {
                String t = p.type(s);
                if (t == null) continue;
                boolean big = t.equals("abominable");
                boolean manual = s.getPersistentDataContainer().has(p.key("manual"));
                boolean alone = manual ? !anyPlayerNear(s.getLocation(), 96) : nearest(s.getLocation(), 96) == null;
                if ((!manual && day && p.b("evil-snowmen.melt-at-sunrise", true)) || p.zones.safe(s.getLocation()) || alone) { melt(s); continue; }
                if (big) { scale(s); abominable(s, clock); }
            }
        }
    }
    void melt(Snowman s) {
        s.getWorld().spawnParticle(Particle.SNOWFLAKE, s.getLocation().add(0, 1, 0), 40, .4, .8, .4, .05);
        s.getWorld().playSound(s.getLocation(), Sound.BLOCK_SNOW_BREAK, 1f, .8f);
        BossBar bar = bars.remove(s.getUniqueId());
        if (bar != null) for (Player pl : Bukkit.getOnlinePlayers()) pl.hideBossBar(bar);
        s.remove();
    }
    private void abominable(Snowman s, int clock) {
        String pre = "evil-snowmen.abominable.";
        if (p.b(pre + "bossbar", true)) {
            BossBar bar = bars.computeIfAbsent(s.getUniqueId(), u -> BossBar.bossBar(Component.text("The Abominable Snowman"), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10));
            bar.progress((float) Math.max(0, Math.min(1, s.getHealth() / s.getAttribute(Attribute.MAX_HEALTH).getValue())));
            for (Player pl : s.getWorld().getPlayers()) { if (pl.getLocation().distanceSquared(s.getLocation()) < 1600) pl.showBossBar(bar); else pl.hideBossBar(bar); }
        }
        if (!(s.getTarget() instanceof Player t) || !p.canHurt(t)) return;
        int c = cool.merge(s.getUniqueId(), 10, Integer::sum);
        double dist = t.getLocation().distance(s.getLocation());
        if (dist <= p.d(pre + "slam.range", 3) && c % (20 * p.i(pre + "slam.every-seconds", 4)) == 0) {
            p.hurt(t, p.d(pre + "slam.damage", 3), s, s, DamageType.MOB_ATTACK);
            Vector v = t.getLocation().toVector().subtract(s.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < .01) v = new Vector(1, 0, 0);
            t.setVelocity(v.normalize().multiply(p.d(pre + "slam.knockback", 1.2)).setY(.5));
            s.getWorld().spawnParticle(Particle.SNOWFLAKE, s.getLocation(), 60, 1.5, .2, 1.5, .1);
            s.getWorld().playSound(s.getLocation(), Sound.BLOCK_SNOW_BREAK, 1.5f, .5f);
        } else if (dist <= p.d(pre + "volley.range", 16) && c % (20 * p.i(pre + "volley.every-seconds", 3)) == 0 && s.hasLineOfSight(t)) {
            Location eye = s.getEyeLocation();
            for (int i = 0; i < p.i(pre + "volley.shards", 3); i++) {
                Vector dir = t.getEyeLocation().toVector().subtract(eye.toVector()).normalize()
                        .add(new Vector(rnd.nextGaussian() * .06, .05 + rnd.nextGaussian() * .03, rnd.nextGaussian() * .06));
                s.launchProjectile(Snowball.class, dir.multiply(1.6));
            }
        }
    }
    void close() {
        for (BossBar bar : bars.values()) for (Player pl : Bukkit.getOnlinePlayers()) pl.hideBossBar(bar);
        bars.clear();
    }
}

package dev.stormrider.naughtylist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.io.File;
import java.util.*;

/** Grinch goblins: steal one stack, flee, and always give it back one way or another. */
final class Goblins implements Listener {
    private final NaughtyListPlugin p;
    private final Random rnd = new Random();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Integer> alone = new HashMap<>(), fleeing = new HashMap<>();
    private final File escrowFile;
    private final YamlConfiguration escrow;
    private final NamespacedKey owner, loot;
    Goblins(NaughtyListPlugin p) {
        this.p = p;
        owner = p.key("owner"); loot = p.key("loot");
        escrowFile = new File(p.getDataFolder(), "stolen.yml");
        escrow = YamlConfiguration.loadConfiguration(escrowFile);
    }
    int count() { return escrow.getKeys(false).size(); }

    Zombie spawn(Location l) {
        if (p.protectedAt(l)) return null;
        return l.getWorld().spawn(l, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
            p.tag(z, "goblin");
            z.setAdult(); z.setShouldBurnInDay(false); z.setCanPickupItems(false); z.setRemoveWhenFarAway(true);
            Threats.attr(z, Attribute.MAX_HEALTH, p.d("grinch-goblins.health", 12)); z.setHealth(p.d("grinch-goblins.health", 12));
            Threats.attr(z, Attribute.SCALE, p.d("grinch-goblins.scale", .6));
            Threats.attr(z, Attribute.MOVEMENT_SPEED, p.d("grinch-goblins.speed", .35));
            Threats.attr(z, Attribute.SPAWN_REINFORCEMENTS, 0);
            var eq = z.getEquipment();
            eq.setHelmet(dyed(Material.LEATHER_HELMET, 0xB02E26)); eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xB02E26));
            eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, 0xB02E26)); eq.setBoots(dyed(Material.LEATHER_BOOTS, 0x1D1D21));
            eq.setItemInMainHand(null);
            eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setLeggingsDropChance(0); eq.setBootsDropChance(0); eq.setItemInMainHandDropChance(0);
            p.name(z, "Grinch Goblin", false);
        });
    }
    private static ItemStack dyed(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        it.editMeta(LeatherArmorMeta.class, meta -> { meta.setColor(Color.fromRGB(rgb)); meta.setUnbreakable(true); });
        return it;
    }
    void restore(Zombie z) { if (has(z)) { z.setRemoveWhenFarAway(false); fleeing.putIfAbsent(z.getUniqueId(), 0); } }
    private boolean has(Entity z) { return z.getPersistentDataContainer().has(loot, PersistentDataType.BYTE_ARRAY); }

    boolean canTarget(Player pl) {
        Long until = cooldown.get(pl.getUniqueId());
        return until == null || until < System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Zombie z) || !"goblin".equals(p.type(z)) || !(e.getEntity() instanceof Player pl)) return;
        e.setCancelled(true); // goblins steal instead of hurting
        if (has(z) || !p.canHurt(pl) || !canTarget(pl)) { z.setTarget(null); return; }
        if (p.b("grinch-goblins.protect-own-claims", true) && p.hooks.ownClaim(pl, pl.getLocation())) {
            pl.sendActionBar(Component.text("The goblin can't steal on your own land!", NamedTextColor.GREEN));
            flee(z, pl);
            return;
        }
        ItemStack taken = steal(pl);
        if (taken == null) { flee(z, pl); return; }
        z.getPersistentDataContainer().set(loot, PersistentDataType.BYTE_ARRAY, taken.serializeAsBytes());
        z.getPersistentDataContainer().set(owner, PersistentDataType.STRING, pl.getUniqueId().toString());
        z.getEquipment().setItemInMainHand(taken.clone());
        z.getEquipment().setItemInMainHandDropChance(0); // dropped by us, exactly once
        z.setRemoveWhenFarAway(false);
        cooldown.put(pl.getUniqueId(), System.currentTimeMillis() + 60_000L * p.i("grinch-goblins.player-cooldown-minutes", 10));
        pl.getWorld().playSound(z.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 1.8f);
        pl.sendMessage(Component.text("A Grinch goblin stole your " + nice(taken) + "! Catch it!", NamedTextColor.RED));
        p.log("Goblin " + z.getUniqueId() + " stole " + taken.getAmount() + "x " + taken.getType() + " from " + pl.getName());
        flee(z, pl);
    }
    private void flee(Zombie z, Player from) {
        fleeing.put(z.getUniqueId(), 0);
        z.setTarget(null);
        run(z, from.getLocation());
    }
    private void run(Zombie z, Location from) {
        Vector away = z.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < .01) away = new Vector(rnd.nextDouble() - .5, 0, rnd.nextDouble() - .5);
        Location to = z.getLocation().add(away.normalize().multiply(16));
        to.setY(z.getWorld().getHighestBlockYAt(to) + 1);
        z.getPathfinder().moveTo(to, 1.3);
    }
    private ItemStack steal(Player pl) {
        PlayerInventory inv = pl.getInventory();
        List<Integer> back = new ArrayList<>(), hot = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || i == inv.getHeldItemSlot() || blocked(it)) continue;
            (i >= 9 ? back : hot).add(i);
        }
        List<Integer> pick = back.isEmpty() ? hot : back;
        if (pick.isEmpty()) return null;
        int slot = pick.get(rnd.nextInt(pick.size()));
        ItemStack it = inv.getItem(slot);
        int n = Math.min(it.getAmount(), Math.max(1, p.i("grinch-goblins.max-steal-amount", 16)));
        ItemStack taken = it.clone(); taken.setAmount(n);
        if (n >= it.getAmount()) inv.setItem(slot, null); else it.setAmount(it.getAmount() - n);
        return taken;
    }
    private boolean blocked(ItemStack it) {
        if (p.b("grinch-goblins.never-steal-named-items", true) && it.hasItemMeta() && it.getItemMeta().hasCustomName()) return true;
        String name = it.getType().name();
        for (String rule : p.getConfig().getStringList("grinch-goblins.never-steal")) {
            String r = rule.toUpperCase(Locale.ROOT);
            if (r.startsWith("*") ? name.endsWith(r.substring(1)) : name.equals(r)) return true;
        }
        return false;
    }
    private static String nice(ItemStack it) { return it.getAmount() + "x " + it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    @EventHandler(ignoreCancelled = true) public void retarget(EntityTargetEvent e) {
        if (e.getEntity() instanceof Zombie z && "goblin".equals(p.type(z)) && fleeing.containsKey(z.getUniqueId()) && e.getTarget() != null) e.setCancelled(true);
    }
    @EventHandler public void death(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || !"goblin".equals(p.type(z))) return;
        e.getDrops().clear();
        ItemStack it = take(z);
        if (it == null) return;
        Player killer = z.getKiller();
        if (killer != null) { p.reward("goblin-kill", killer); p.log("Goblin killed by " + killer.getName()); }
        giveBack(z, it); // straight back to the owner (killed by anyone, lava, void, /kill): never lost
    }
    /** removed without dying (e.g. /minecraft:kill counts as death; plugins removing it do not): give it back */
    @EventHandler public void removed(EntityRemoveEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || !"goblin".equals(p.type(z))) return;
        if (p.getConfig().getBoolean("debug")) p.getLogger().info("goblin remove cause " + e.getCause());
        var c = e.getCause();
        if (c == EntityRemoveEvent.Cause.UNLOAD || c == EntityRemoveEvent.Cause.DEATH || c == EntityRemoveEvent.Cause.PLAYER_QUIT) return;
        ItemStack it = take(z);
        if (it != null) giveBack(z, it);
    }
    private ItemStack take(Zombie z) {
        byte[] b = z.getPersistentDataContainer().get(loot, PersistentDataType.BYTE_ARRAY);
        if (b == null) return null;
        z.getPersistentDataContainer().remove(loot);
        z.getEquipment().setItemInMainHand(null);
        fleeing.remove(z.getUniqueId());
        return ItemStack.deserializeBytes(b);
    }
    private UUID ownerOf(Zombie z) {
        String s = z.getPersistentDataContainer().get(owner, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }
    private void giveBack(Zombie z, ItemStack it) {
        UUID id = ownerOf(z);
        Player pl = id == null ? null : Bukkit.getPlayer(id);
        if (pl != null) {
            for (ItemStack left : pl.getInventory().addItem(it).values()) pl.getWorld().dropItem(pl.getLocation(), left);
            pl.sendMessage(Component.text("Your " + nice(it) + " came back to you from the goblin!", NamedTextColor.GREEN));
        } else if (id != null) {
            List<String> list = new ArrayList<>(escrow.getStringList(id.toString()));
            list.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            escrow.set(id.toString(), list);
            saveEscrow();
        } else drop(z.getLocation(), it);
        p.log("Goblin loot " + it.getType() + " returned to owner " + id);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        List<String> list = escrow.getStringList(e.getPlayer().getUniqueId().toString());
        if (list.isEmpty()) return;
        for (String s : list) for (ItemStack left : e.getPlayer().getInventory().addItem(ItemStack.deserializeBytes(Base64.getDecoder().decode(s))).values())
            e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), left);
        escrow.set(e.getPlayer().getUniqueId().toString(), null);
        saveEscrow();
        e.getPlayer().sendMessage(Component.text("Items a Grinch goblin stole were returned to you.", NamedTextColor.GREEN));
    }
    private void saveEscrow() { try { escrow.save(escrowFile); } catch (Exception ex) { p.getLogger().warning("Could not save stolen.yml: " + ex); } }
    private Item drop(Location l, ItemStack it) {
        Item d = l.getWorld().dropItem(l, it);
        d.setUnlimitedLifetime(true); d.setGlowing(true); d.setInvulnerable(true);
        d.customName(Component.text("Stolen Loot", NamedTextColor.GOLD)); d.setCustomNameVisible(true);
        return d;
    }
    /** goblin gives up: leave the loot as a glowing, never-despawning item and tell the owner */
    private void escape(Zombie z) {
        ItemStack it = take(z);
        UUID id = ownerOf(z);
        Player pl = id == null ? null : Bukkit.getPlayer(id);
        if (it != null && pl == null && id != null) giveBack(z, it); // owner offline: keep it safe for their next login
        else if (it != null) {
            drop(z.getLocation(), it);
            if (pl != null) pl.sendMessage(Component.text("The goblin got away but dropped your " + nice(it) + " near "
                    + (z.getLocation().getBlockX() / 10 * 10) + ", " + (z.getLocation().getBlockZ() / 10 * 10) + ". It glows and won't disappear.", NamedTextColor.GOLD));
            p.log("Goblin escaped and dropped " + it.getType() + " at " + z.getLocation().getBlockX() + " " + z.getLocation().getBlockY() + " " + z.getLocation().getBlockZ());
        }
        z.getWorld().spawnParticle(Particle.LARGE_SMOKE, z.getLocation().add(0, .5, 0), 20, .3, .4, .3, .02);
        fleeing.remove(z.getUniqueId()); alone.remove(z.getUniqueId());
        z.remove();
    }

    void tick(int clock) {
        for (World w : Bukkit.getWorlds()) {
            if (!p.world(w)) continue;
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) {
                if (!"goblin".equals(p.type(z))) continue;
                UUID u = z.getUniqueId();
                boolean carrying = has(z);
                if (p.zones.safe(z.getLocation()) || !p.night(w)) { escape(z); continue; }
                if (!fleeing.containsKey(u)) continue;
                int t = fleeing.merge(u, 10, Integer::sum);
                Player near = p.threats.nearest(z.getLocation(), p.d("grinch-goblins.escape.distance", 40));
                int a = near == null ? alone.merge(u, 10, Integer::sum) : 0;
                if (near != null) alone.put(u, 0);
                if (!carrying) { if (t > 200) escape(z); continue; }
                if (a >= 20 * p.i("grinch-goblins.escape.seconds", 30) || t >= 20 * p.i("grinch-goblins.escape.max-seconds", 60)) { escape(z); continue; }
                z.setTarget(null);
                if (near != null && t % 20 == 0) run(z, near.getLocation());
            }
        }
    }
    void close() { saveEscrow(); }
}

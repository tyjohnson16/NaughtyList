package dev.stormrider.naughtylist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class NaughtyListPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    Hooks hooks; SafeZones zones; Blizzard blizzard; Threats threats; Presents presents; Goblins goblins; Posts posts;
    private int clock;
    NamespacedKey key(String s) { return new NamespacedKey(this,s); }
    String type(Entity e) { return e.getPersistentDataContainer().get(key("type"),PersistentDataType.STRING); }
    void tag(Entity e,String type) { e.getPersistentDataContainer().set(key("type"),PersistentDataType.STRING,type); }
    boolean night(World w) { long t=w.getTime();return t>=13000 && t<23000; }
    boolean world(World w) { var list=getConfig().getStringList("worlds");return w.getDifficulty()!=Difficulty.PEACEFUL && (list.contains(w.getName()) || list.contains("auto") && hooks.christmas(w)); }
    boolean player(Player p) {return p.isOnline() && !p.isDead() && p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.SPECTATOR && !p.hasPermission("naughtylist.ignore") && p.getMetadata("vanished").stream().noneMatch(v->v.asBoolean());}
    double d(String key,double fallback) {return getConfig().getDouble(key,fallback);}
    int i(String key,int fallback) {return getConfig().getInt(key,fallback);}
    boolean b(String key,boolean fallback){return getConfig().getBoolean(key,fallback);}
    void name(Entity e,String text,boolean boss) {e.customName(Component.text(text,boss?NamedTextColor.GOLD:NamedTextColor.RED));e.setCustomNameVisible(false);}
    static double dist(Location a,Location b){return a.getWorld()==b.getWorld()?a.distanceSquared(b):Double.MAX_VALUE;}
    boolean protectedAt(Location l) {return zones.safe(l)||hooks.spawnDenied(l);}
    boolean canHurt(Player p) {return world(p.getWorld()) && player(p) && !zones.safe(p.getLocation());}
    void reward(String event,Player player) {if(player!=null) for(String cmd:getConfig().getStringList("rewards."+event)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd.replace("%player%",player.getName()));}
    void log(String s){getLogger().info(s);}
    /** deal exactly `amount` (before armor) from a mob, undoing vanilla's difficulty scaling so config numbers are what players get */
    void hurt(Player pl,double amount,LivingEntity by,Entity direct,org.bukkit.damage.DamageType type){
        double x=switch(pl.getWorld().getDifficulty()){case HARD->amount/1.5;case EASY->amount<=2?amount:2*(amount-1);default->amount;};
        var b=org.bukkit.damage.DamageSource.builder(type).withCausingEntity(by).withDirectEntity(direct==null?by:direct);
        pl.damage(x,b.build());
    }
    @Override public void onEnable(){
        saveDefaultConfig(); hooks=new Hooks(this);zones=new SafeZones(this);blizzard=new Blizzard(this);goblins=new Goblins(this);
        threats=new Threats(this);presents=new Presents(this);posts=new Posts(this);
        for(var listener:List.of(blizzard,threats,presents,goblins,posts))getServer().getPluginManager().registerEvents(listener,this);
        getCommand("naughty").setExecutor(this);getCommand("naughty").setTabCompleter(this);
        getServer().getScheduler().runTaskTimer(this,()->{
            clock+=10;
            blizzard.tick(clock);threats.tick(clock);goblins.tick(clock);posts.tick(clock);
        },20,10);
        log("NaughtyList ready. XmasWorld API="+hooks.xmas+", WorldGuard="+hooks.wg+", GriefPrevention="+hooks.gp+". Use /naughty status.");
    }
    @Override public void onDisable(){
        if(blizzard!=null)blizzard.close();
        if(threats!=null)threats.close();
        if(goblins!=null)goblins.close();
        if(posts!=null)posts.save();
    }
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(a.length==0){help(s);return true;}
        try {switch(a[0].toLowerCase(Locale.ROOT)){
            case "reload" -> {blizzard.close();reloadConfig();zones.clear();s.sendMessage("NaughtyList config reloaded. Active blizzards ended safely.");}
            case "status" -> {s.sendMessage("NaughtyList "+getPluginMeta().getVersion()+"; XmasWorld API="+hooks.xmas+", WG="+hooks.wg+", GP="+hooks.gp);for(World w:Bukkit.getWorlds())if(world(w))s.sendMessage(w.getName()+": blizzard="+blizzard.active(w)+", threats="+w.getLivingEntities().stream().filter(e->type(e)!=null).count()+", posts="+posts.count(w));s.sendMessage("Goblin escrow entries="+goblins.count()+"; commands: /naughty help");}
            case "blizzard" -> {World w=a.length>2?Bukkit.getWorld(a[2]):s instanceof Player p?p.getWorld():Bukkit.getWorlds().getFirst();if(w==null||!world(w)){s.sendMessage("Not an enabled Christmas world.");break;}if(a.length>1&&a[1].equals("start")){blizzard.start(w,true);s.sendMessage("Blizzard started.");}else{blizzard.stop(w);s.sendMessage("Blizzard stopped.");}}
            case "present" -> {
                if(a.length>1&&a[1].equalsIgnoreCase("scan")){Location at=s instanceof Player p?p.getLocation():Bukkit.getWorlds().getFirst().getSpawnLocation();int r=Math.max(8,Math.min(256,a.length>2?Integer.parseInt(a[2]):64));presents.scan(at,r).forEach(s::sendMessage);break;}
                if(!(s instanceof Player p)){s.sendMessage("Run in-game while looking at a present barrel, or use /naughty present scan [radius].");break;}
                Block t=p.getTargetBlockExact(8);
                if(t!=null&&t.getType()!=Material.BARREL){Block f=null;for(int dy=-1;dy>=-2&&f==null;dy--)if(t.getRelative(0,dy,0).getType()==Material.BARREL)f=t.getRelative(0,dy,0);for(int dx=-1;dx<=1&&f==null;dx++)for(int dy=-1;dy<=1&&f==null;dy++)for(int dz=-1;dz<=1&&f==null;dz++)if(t.getRelative(dx,dy,dz).getType()==Material.BARREL)f=t.getRelative(dx,dy,dz);if(f!=null)t=f;}
                if(t==null||t.getType()!=Material.BARREL){s.sendMessage("Look at a present's barrel (the top centre block) within 8 blocks.");break;}
                presents.explain(t).forEach(s::sendMessage);
            }
            case "zone" -> {if(s instanceof Player p)s.sendMessage("Safe zone="+zones.safe(p.getLocation())+", own GP claim="+hooks.ownClaim(p,p.getLocation()));else s.sendMessage("Run in-game.");}
            case "spawn" -> {
                if(a.length<2){help(s);break;}
                Location l;
                if(a.length>=6){World w=Bukkit.getWorld(a[2]);if(w==null){s.sendMessage("Unknown world");break;}l=new Location(w,Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]));}
                else if(s instanceof Player p)l=p.getLocation().add(p.getLocation().getDirection().multiply(4));else{ s.sendMessage("Console: /naughty spawn <type> <world> <x> <y> <z>");break;}
                if(!world(l.getWorld())){s.sendMessage("Blocked: NaughtyList is not enabled in this world (see worlds: in config.yml; Peaceful worlds are always off).");break;}
                if(zones.safe(l)){s.sendMessage("Blocked: that spot is in a NaughtyList safe zone (spawn area, a safe Christmas build or a listed WorldGuard region), where threats never spawn. Move outside it; /naughty zone check tells you where you are.");break;}
                if(hooks.spawnDenied(l)){s.sendMessage("Blocked: WorldGuard denies mob-spawning here.");break;}
                Entity e=threats.spawn(a[1],l);s.sendMessage(e==null?"Spawn denied or unknown type":"Spawned "+a[1]+" "+e.getUniqueId());
            }
            default -> help(s);
        }}catch(NumberFormatException e){s.sendMessage("Expected numeric coordinates.");}
        return true;
    }
    private void help(CommandSender s){s.sendMessage("/naughty status | reload | zone check | present [scan [radius]] | blizzard start|stop [world] | spawn snowman|abominable|goblin|guard|captain [world x y z]");}
    public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){List<String> l=args.length==1?List.of("status","reload","zone","present","blizzard","spawn"):args.length==2&&args[0].equals("present")?List.of("scan"):args.length==2&&args[0].equals("spawn")?List.of("snowman","abominable","goblin","guard","captain"):List.of();return l.stream().filter(x->x.startsWith(args[args.length-1])).toList();}
}

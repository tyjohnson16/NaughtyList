// Test bot: node bot.js <name> [port]. Reads commands from ./cmd-<name>.fifo (one per line), logs to stdout.
const mineflayer = require('mineflayer');
const fs = require('fs');
const name = process.argv[2] || 'Tester', port = +(process.argv[3] || 25565);
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version: '26.1.2', auth: 'offline' });
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
bot.on('login', () => log('LOGIN'));
bot.on('spawn', () => { log('SPAWN', bot.entity.position.toString()); listen(); });
bot.on('messagestr', (m, pos) => log('MSG', pos, m));
bot.on('title', (t, type) => log('TITLE', type, typeof t === 'string' ? t : JSON.stringify(t)));
bot.on('bossBarCreated', b => log('BOSSBAR+', JSON.stringify(b.title))); bot.on('bossBarDeleted', b => log('BOSSBAR-'));
bot.on('actionBar', m => log('ACTIONBAR', m.toString()));
bot.on('health', () => log('HEALTH', bot.health.toFixed(1)));
bot.on('kicked', r => log('KICKED', JSON.stringify(r)));
bot.on('error', e => log('ERROR', e.message));
bot.on('end', r => { log('END', r); process.exit(0); });
bot._client.on('set_entity_data', () => {});
bot._client.on('packet', (d, m) => { if (process.env.ATTRLOG && /attr|remove_entit|destroy/.test(m.name)) log('PKT', m.name, JSON.stringify(d).slice(0, 300)); });
bot._client.on('update_attributes', d => { if (process.env.ATTRLOG) log('UPDATE_ATTR', d.entityId, JSON.stringify(d.properties.map(x => [x.key, x.value]))); });
bot._client.on('spawn_entity', d => { if (process.env.ATTRLOG) log('SPAWN_ENTITY', d.entityId, d.type); });
function inv() { return bot.inventory.items().map(i => `${i.name}x${i.count}@${i.slot}`).join(' '); }
function near(nameMatch) {
  let best = null, bd = 1e9;
  for (const e of Object.values(bot.entities)) {
    if (e === bot.entity || !e.position) continue;
    if (nameMatch && !(e.name || '').includes(nameMatch)) continue;
    const d = e.position.distanceTo(bot.entity.position);
    if (d < bd) { bd = d; best = e; }
  }
  return best;
}
let attacking = null;
setInterval(() => {
  if (!attacking) return;
  const e = bot.entities[attacking];
  if (!e || !e.isValid) { log('TARGET GONE'); attacking = null; return; }
  bot.lookAt(e.position.offset(0, 0.8, 0), true);
  if (e.position.distanceTo(bot.entity.position) < 3.5) bot.attack(e);
}, 500);
async function run(line) {
  const [cmd, ...a] = line.trim().split(/\s+/);
  try {
    if (cmd === 'chat') bot.chat(a.join(' '));
    else if (cmd === 'inv') log('INV', inv());
    else if (cmd === 'pos') log('POS', bot.entity.position.toString(), 'freeze?', JSON.stringify(bot.entity.metadata ? bot.entity.metadata[7] : null));
    else if (cmd === 'ents') log('ENTS', Object.values(bot.entities).filter(e => e !== bot.entity && e.position && e.position.distanceTo(bot.entity.position) < 40).map(e => `${e.name}#${e.id}@${e.position.distanceTo(bot.entity.position).toFixed(1)}`).join(' '));
    else if (cmd === 'attack') { const e = near(a[0]); attacking = e ? e.id : null; log('ATTACK', e ? `${e.name}#${e.id}` : 'none'); }
    else if (cmd === 'stop') attacking = null;
    else if (cmd === 'open') {
      const b = bot.blockAt(new (require('vec3'))(+a[0], +a[1], +a[2]));
      log('OPENING', b && b.name);
      const w = await Promise.race([bot.openContainer(b), new Promise(r => setTimeout(() => r(null), 2500))]);
      if (!w) log('OPEN', 'no window (cancelled?)');
      else { log('OPEN', w.containerItems().map(i => `${i.name}x${i.count}`).join(' ')); w.close(); }
    } else if (cmd === 'dig') {
      const b = bot.blockAt(new (require('vec3'))(+a[0], +a[1], +a[2]));
      log('DIG', b && b.name); await bot.dig(b, true); log('DUG');
    } else if (cmd === 'look') bot.lookAt(new (require('vec3'))(+a[0], +a[1], +a[2]), true);
    else if (cmd === 'quit') bot.quit();
    else if (cmd === 'attrs') { const e = near(a[0]); log('ATTRS', e ? `${e.name}#${e.id}` : 'none', e ? JSON.stringify(e.attributes) : ''); }
    else if (cmd === 'tab') { const q = line.replace(/^\s*tab /, '').replace(/ _$/, ' '); const r = await bot.tabComplete(q, true, false, 5000); log('TAB', JSON.stringify(q), JSON.stringify(r.map(x => x.match || x))); }
  } catch (e) { log('CMDERR', cmd, e.message); }
}
function listen() {
  const f = `${__dirname}/cmd-${name}.fifo`;
  try { fs.unlinkSync(f); } catch {}
  require('child_process').execSync(`mkfifo ${f}`);
  const open = () => { const s = fs.createReadStream(f); let buf = ''; s.on('data', d => { buf += d; let i; while ((i = buf.indexOf('\n')) >= 0) { run(buf.slice(0, i)); buf = buf.slice(i + 1); } }); s.on('end', open); };
  open();
}

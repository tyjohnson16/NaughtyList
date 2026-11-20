# NaughtyList test bot

A small [Mineflayer](https://github.com/PrismarineJS/mineflayer) client used to exercise NaughtyList on a local Paper server.

```bash
npm install
node bot.js Tester 25565
```

It connects to `127.0.0.1` in offline mode, using Minecraft protocol version 26.1.2. Commands are read from `cmd-<name>.fifo` (one command per line).

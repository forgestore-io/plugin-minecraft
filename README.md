# ForgeStore — Minecraft Plugin

Bukkit / Spigot / Paper / Folia plugin for automatic purchase delivery.

## Installation
1. Drop `ForgeStore-Minecraft.jar` into your `plugins/` folder
2. Restart the server
3. Set your secret: `/forgestore secret YOUR_KEY`
4. Verify: `/forgestore info`

## Commands
| Command | Description |
|---------|-------------|
| `/forgestore secret <key>` | Set your server secret key |
| `/forgestore check` | Force queue check |
| `/forgestore info` | Show store info |
| `/forgestore reload` | Reload config |

## Example Commands
```
lp user {player} parent set vip
give {player} diamond 64
eco give {player} 5000
```

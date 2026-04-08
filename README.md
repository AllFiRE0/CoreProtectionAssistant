# CoreProtectionAssistant

[![Build Status](https://img.shields.io/github/actions/workflow/status/AllFiRE0/CoreProtectionAssistant/build.yml)](https://github.com/AllFiRE0/CoreProtectionAssistant/actions)
[![Version](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/AllFiRE0/CoreProtectionAssistant/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)

Advanced staff monitoring and player protection system for Minecraft servers.

---

## 📖 Overview

CoreProtectionAssistant (CPA) is a **zero-trust** staff monitoring and player reputation system. It tracks staff actions, analyzes player behavior, and provides detailed statistics without creating new menus — all data is accessible via PlaceholderAPI for use in other plugins (CMI, DeluxeMenus, ItemsAdder, etc.).

**Key principle:** The plugin doesn't store data that already exists elsewhere — it reads from CoreProtect, CMI, and LuckPerms.

---

## 👤 Author

**AllF1RE**
- GitHub: [AllFiRE0](https://github.com/AllFiRE0)

---

## ✨ Features

### Staff Monitoring
- Track all staff commands (`/give`, `/gamemode`, `/ban`, `/mute`, `/kick`, `/invsee`, etc.)
- **Abuse Score System** — calculates a 0-100 score based on suspicious actions
- Automatic warnings when abuse score exceeds thresholds
- Detailed staff statistics with full action history

### Player Protection
- **Unified Chat Rules** — single configuration for both punishments and apologies
- Regex-based detection with priorities and recidivism tracking
- Automatic warning clearance for apologies
- **Apologies Tracking** — counts apologies and calculates repentance ratio (apologies / violations)
- **Report System** with anti-abuse protection
- Automatic violator analysis based on playtime, warnings, and report count
- **Same IP & Alt Detection** — detects players with same IP and similar nicknames
- **Prohibited Permissions Check** — alerts when staff members have dangerous permissions

### 🛡️ Grief Detection (NEW)
- **Direct CoreProtect database reading** — no duplicate storage
- **Block history analysis** — checks if other players interacted with the same block
- **Similar nickname detection** — `1=i=l`, `0=o`, `5=s=6`, etc.
- **Configurable tracked blocks** — chests, furnaces, shulkers, hoppers, etc.
- **Separate punishments for players and staff**

### 🤖 ChatBot (NEW)
- Fully configurable chatbot that responds to player messages
- **Rule-based system** with priorities, permissions, and cooldowns
- **Condition support** — use PAPI placeholders with comparisons (`>`, `<`, `==`, `contains`, `AND`, `OR`)
- **Symbol prefix** — respond only to messages starting with `!`, `?`, etc.
- **Random responses** — choose from a list of possible answers
- **Delayed responses** — bot can wait before replying
- **Chance-based triggers** — probability of response in percentage
- **Context variables** — `%player_name%`, `%player_world%`, `%player_time%`, `%target%`, `%message%`
- All command prefixes supported: `message!`, `broadcast!`, `asConsole!`, `sound!`, `title!`, `actionbar!`

### ⏱️ Temporary Warnings & Silent Mode (NEW)
- **Temporary warnings** — `-t:1d` (day), `-t:1h` (hour), `-t:1m` (minute), `-t:1s` (second)
- **Silent mode** — `-s` flag suppresses all command output
- Example: `/cpa warn Player Griefing -t:20m -s`

### Statistics & Integration
- CoreProtect integration (via reflection and direct DB — no hard dependency)
- PlaceholderAPI support (35+ placeholders)
- SQLite (with HikariCP connection pool) and MySQL support
- Optional integrations: CMI, LuckPerms

### Quality of Life
- **Full localization** — all messages in `lang.yml` (supports HEX colors `&#RRGGBB`)
- **Message toggling** — disable any message by setting it to `""` or `"none"`
- **Offline player tab-complete** — all commands suggest offline players (up to 20)
- **Centralized reload** — `/cpa reload` reloads all configurations including ChatBot
- **Debug mode** — `debug: true/false` in config for troubleshooting

---

## 📋 Requirements

| Requirement | Version | Required |
|-------------|---------|----------|
| Paper / Leaf | 1.20.4+ | ✅ Required |
| Java | 21+ | ✅ Required |
| CoreProtect | 22.4+ | ⭐ Recommended |
| PlaceholderAPI | 2.11+ | ⭐ Recommended |
| CMI | 9.7+ | ⭕ Optional |
| LuckPerms | 5.4+ | ⭕ Optional |

---

## 📥 Installation

1. Download the latest `CoreProtectionAssistant-1.1.0.jar` from [Releases](https://github.com/AllFiRE0/CoreProtectionAssistant/releases)
2. Place it in your server's `plugins/` folder
3. Install recommended plugins (CoreProtect, PlaceholderAPI)
4. Restart your server
5. Configure `plugins/CoreProtectionAssistant/config.yml`
6. Use `/cpa reload` to apply changes

---

## ⚙️ Configuration Files

| File | Description |
|------|-------------|
| `config.yml` | Main configuration (database, tracked commands, abuse weights, grief detection) |
| `lang.yml` | All plugin messages (fully customizable, supports HEX colors) |
| `chattrules.yml` | Chat filter rules (punishments + apologies) |
| `reports.yml` | Report categories and anti-abuse settings |
| `chatbot.yml` | ChatBot rules and responses (NEW) |

### Auto Cleanup (`config.yml` section)
```yaml
cleanup:
  player_commands: 30   # Days to keep player commands
  staff_actions: 90     # Days to keep staff actions
  chat_violations: 30   # Days to keep chat violations
  apologies: 30         # Days to keep apologies
  grief_actions: 30     # Days to keep grief actions
```

### Grief Detection (`config.yml` section)
```yaml
grief_detection:
  enabled: true
  allowed_regions:
    - "__global__"
    - "otherRegion"
  tracked_blocks:
    - "CHEST"
    - "FURNACE"
    - "BARREL"
  grief_commands:
    - "asConsole! cpa warn %player_name% Griefing detected -t:20m -s"
  staff_grief_commands:
    - "asConsole! cpa warn %player_name% Staff griefing -t:1h"
  abuse_weight: 10
  min_time_between_actions: 5
```

---

## 🎮 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cpa` | - | Show help menu |
| `/cpa stats <player>` | `cpa.moder` | View player statistics |
| `/cpa resetstats <player> <type>` | `cpa.staff` | Reset player statistics (commands/all/ban/mute/kick/give/gm/rating/warn/free) |
| `/cpa staff <player>` | `cpa.staff` | View staff member statistics |
| `/cpa top <type> [page]` | `cpa.moder` | View leaderboards |
| `/cpa check <player>` | `cpa.moder` | Quick player check |
| `/cpa warn <player> [reason] [-t:1d\|1h\|1m\|1s] [-s]` | `cpa.warn` | Issue warning (temporary or permanent, silent mode) |
| `/cpa warn clear <player> <amount> [-s]` | `cpa.warn.clear` | Clear warnings (silent mode supported) |
| `/cpa warn list <player> [-s]` | `cpa.moder` | List active warnings (silent mode supported) |
| `/cpa reload` | `cpa.reload` | Reload configuration |
| `/cpa report <player> <reason>` | `cpa.report` | Report a player (staff) |
| `/report <player> <reason>` | `cpa.report` | Report a player |

**Aliases:** `/cpa`, `/protect`, `/adminprotect`, `/aa`

---

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `cpa.moder` | op | Access to player statistics |
| `cpa.staff` | op | Access to staff statistics (owner only!) |
| `cpa.resetstats` | op | Can reset player statistics |
| `cpa.warn` | op | Can issue warnings |
| `cpa.warn.clear` | op | Can clear warnings |
| `cpa.report` | true | Can use `/report` |
| `cpa.report.see` | op | Can see report notifications |
| `cpa.report.tp` | op | Can teleport to report location |
| `cpa.reload` | op | Can reload configuration |
| `cpa.bypass.chat` | op | Bypass chat filter |
| `cpa.chatbot.use` | true | Can interact with ChatBot (NEW) |
| `cpa.chatbot.bypass` | op | Ignored by ChatBot (NEW) |

---

## 📊 Placeholders (PlaceholderAPI)

### Player Statistics
| Placeholder | Description |
|-------------|-------------|
| `%cpa_player_blocks_broken%` | Total blocks broken |
| `%cpa_player_blocks_placed%` | Total blocks placed |
| `%cpa_player_chests_opened%` | Total containers opened |
| `%cpa_player_commands_count%` | Total commands used (from internal DB) |
| `%cpa_player_deaths%` | Total deaths |
| `%cpa_player_kills%` | Total kills |
| `%cpa_player_first_seen%` | First join date |
| `%cpa_player_last_seen%` | Last seen date |
| `%cpa_player_warnings_count%` | Active warnings |
| `%cpa_player_violations_count%` | Chat violations |
| `%cpa_player_apologies_count%` | Total apologies |
| `%cpa_player_violations_apologies_ratio%` | Repentance ratio (apologies / violations) |
| `%cpa_player_time_since_last_violation%` | Seconds since last violation |
| `%cpa_player_cmd_<command>%` | Usage count of specific command (e.g., `%cpa_player_cmd_tpa%`) |
| `%cpa_player_reports_against%` | Total reports received |
| `%cpa_player_reports_filed%` | Total reports sent |
| `%cpa_player_reports_<category>%` | Reports received by category (e.g., `%cpa_player_reports_griefing%`) |
| `%cpa_reports_count_<category>_<player>%` | Reports by category for specific player (e.g., `%cpa_reports_count_griefing_AllF1RE%`) |

### Staff Statistics
| Placeholder | Description |
|-------------|-------------|
| `%cpa_staff_bans_count%` | Total bans issued |
| `%cpa_staff_mutes_count%` | Total mutes issued |
| `%cpa_staff_kicks_count%` | Total kicks issued |
| `%cpa_staff_gives_count%` | Total /give uses |
| `%cpa_staff_abuse_score%` | Current abuse score (0-100) |
| `%cpa_staff_warnings_count%` | Warnings received |

---

## 🗄️ Database

Supports both **SQLite** (default) and **MySQL**.

### Tables Created
- `cpa_command_logs` — All tracked commands
- `cpa_staff_actions` — Staff activity log
- `cpa_warnings` — Player and staff warnings
- `cpa_reports` — Player reports
- `cpa_chat_violations` — Chat filter violations
- `cpa_apologies` — Apology history
- `cpa_abuse_scores` — Staff abuse scores
- `cpa_grief_actions` — Grief detection log
- `cpa_player_commands` — All tracked player and staff commands
- `cpa_player_commands` — All tracked player and staff commands

---

### Acknowledgements
- PaperMC — Server software
- CoreProtect — Block logging
- PlaceholderAPI — Placeholder support
- CMI — Economy & playtime
- LuckPerms — Permission management
- WorldGuard — Region protection

---

## 🔨 Building from Source

### Requirements
- JDK 21
- Maven 3.9+

### Build Commands

---

## ❓ FAQ

**Q: Why "zero-trust"?**  
A: The plugin assumes staff members might abuse their powers. It tracks everything and calculates an abuse score to alert owners.

**Q: Does it work without CoreProtect?**  
A: Yes, but block/chest/command statistics and grief detection will be unavailable.

**Q: Can I customize the abuse score weights?**  
A: Yes, in config.yml under abuse_weights.

**Q: How do I add new chat rules?**  
A: Edit chattrules.yml and use /cpa reload.

**Q: What is "recidivism"?**  
A: Repeated violations within a time window result in harsher punishments.

**Q: How do I disable a specific message?**  
A: Set it to "" or "none" in lang.yml.

**Q: How do I reset a player's statistics?**  
A: Use `/cpa resetstats <player> <type>` (commands, all, ban, mute, kick, give, gm, rating, warn, free).

```bash






Advanced staff monitoring and player protection system for Minecraft servers.

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
- **Report System** with anti-abuse protection
- Automatic violator analysis based on playtime, warnings, and report count

### Statistics & Integration
- CoreProtect integration (via reflection — no hard dependency)
- PlaceholderAPI support (30+ placeholders)
- SQLite and MySQL support
- Optional integrations: CMI, LuckPerms, STCP

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
| STCP | 1.0+ | ⭕ Optional |

---

## 📥 Installation

1. Download the latest `CoreProtectionAssistant-1.0.0.jar` from [Releases](https://github.com/AllFiRE0/CoreProtectionAssistant/releases)
2. Place it in your server's `plugins/` folder
3. Install recommended plugins (CoreProtect, PlaceholderAPI)
4. Restart your server
5. Configure `plugins/CoreProtectionAssistant/config.yml`
6. Use `/cpa reload` to apply changes

---

## ⚙️ Configuration

### Main Config (`config.yml`)
```yaml
database:
  type: sqlite  # or mysql
  
tracked_moder_commands:
  - give
  - gamemode
  - ban
  # ...

abuse_weights:
  ban_without_reason: 10
  self_give_items: 8
  # ...
```

### Chat Rules (`chattrules.yml`)
Single unified configuration for all chat interactions:
```yaml
rules:
  severe_insult:
    priority: 100
    regex: "(?i)(fuck you|motherfucker)"
    action: "punish"
    punishment: "ban"
    duration_ticks: 6048000
    
  simple_apology:
    priority: 15
    regex: "(?i)(sorry|my bad)"
    action: "apology"
    warnings_clear: 1
```

### Reports (`reports.yml`)
```yaml
categories:
  griefing:
    regex: "(?i)(grief|stole|destroyed)"
    weight: 3
    
anti_abuse:
  enabled: true
  threshold: 5
```

### Language (`lang.yml`)
All messages are customizable with full color support (`&a`, `&c`, `&#RRGGBB`).

---

## 🎮 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cpa` | - | Show help menu |
| `/cpa stats <player>` | `cpa.moder` | View player statistics |
| `/cpa staff <player>` | `cpa.staff` | View staff member statistics |
| `/cpa top <type>` | `cpa.moder` | View leaderboards (blocks, kills, playtime) |
| `/cpa check <player>` | `cpa.moder` | Quick player check (warnings, reports, alts) |
| `/cpa warn <player> [reason]` | `cpa.warn` | Issue warning to player |
| `/cpa warn clear <player> <amount>` | `cpa.warn.clear` | Clear warnings |
| `/cpa warn list <player>` | `cpa.moder` | List active warnings |
| `/cpa reload` | `cpa.reload` | Reload configuration |
| `/report <player> <reason>` | `cpa.report` | Report a player |

**Aliases:** `/cpa`, `/protect`, `/adminprotect`, `/aa`

---

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `cpa.moder` | op | Access to player statistics |
| `cpa.staff` | op | Access to staff statistics (owner only!) |
| `cpa.warn` | op | Can issue warnings |
| `cpa.warn.clear` | op | Can clear warnings |
| `cpa.report` | true | Can use `/report` |
| `cpa.report.see` | op | Can see report notifications |
| `cpa.report.tp` | op | Can teleport to report location |
| `cpa.reload` | op | Can reload configuration |
| `cpa.bypass.chat` | op | Bypass chat filter |

---

## 📊 Placeholders (PlaceholderAPI)

### Player Statistics
| Placeholder | Description |
|-------------|-------------|
| `%cpa_player_blocks_broken%` | Total blocks broken |
| `%cpa_player_blocks_placed%` | Total blocks placed |
| `%cpa_player_chests_opened%` | Total containers opened |
| `%cpa_player_commands_count%` | Total commands used |
| `%cpa_player_deaths%` | Total deaths |
| `%cpa_player_kills%` | Total kills |
| `%cpa_player_first_seen%` | First join date |
| `%cpa_player_last_seen%` | Last seen date |
| `%cpa_player_warnings_count%` | Active warnings |
| `%cpa_player_violations_count%` | Chat violations |
| `%cpa_player_time_since_last_violation%` | Seconds since last violation |
| `%cpa_player_cmd_<command>%` | Usage count of specific command |

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

### MySQL Setup
```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: cpa
    username: root
    password: yourpassword
```

### Tables Created
- `cpa_command_logs` — All tracked commands
- `cpa_staff_actions` — Staff activity log
- `cpa_warnings` — Player and staff warnings
- `cpa_reports` — Player reports
- `cpa_chat_violations` — Chat filter violations
- `cpa_apologies` — Apology history
- `cpa_abuse_scores` — Staff abuse scores

---

## 🔨 Building from Source

### Requirements
- JDK 21
- Maven 3.9+

### Build Commands
```bash
# Clone repository
git clone https://github.com/AllFiRE0/CoreProtectionAssistant.git
cd CoreProtectionAssistant

# Build with Maven
mvn clean package

# Output: target/CoreProtectionAssistant-1.0.0.jar
```

### Windows Users
Run `build.bat` — it will automatically build the plugin.

---

## 🧩 Integration Details

### CoreProtect (Reflection)
No hard dependency — uses reflection to access CoreProtect API. If CoreProtect is not installed, related statistics will show `0`.

### PlaceholderAPI
All placeholders are registered automatically. Use `%cpa_<category>_<param>%`.

### LuckPerms
Used for permission checking and group detection. Falls back gracefully if not installed.

### CMI
Used for balance, playtime, and IP address data. Optional.

### STCP
Used for lag machine detection statistics. Optional.

---

## ❓ FAQ

### Q: Why "zero-trust"?
**A:** The plugin assumes staff members might abuse their powers. It tracks everything and calculates an abuse score to alert owners.

### Q: Does it work without CoreProtect?
**A:** Yes, but block/chest/command statistics will be unavailable.

### Q: Can I customize the abuse score weights?
**A:** Yes, in `config.yml` under `abuse_weights`.

### Q: How do I add new chat rules?
**A:** Edit `chattrules.yml` and use `/cpa reload`.

### Q: What is "recidivism"?
**A:** Repeated violations within a time window result in harsher punishments.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgements

- [PaperMC](https://papermc.io/) — Server software
- [CoreProtect](https://www.coreprotect.net/) — Block logging
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — Placeholder support
- [CMI](https://www.spigotmc.org/resources/cmi-298-commands-insane-kits-portals-essentials-economy-mysql-sqlite-much-more.3742/) — Economy & playtime
- [LuckPerms](https://luckperms.net/) — Permission management

---

## 🐛 Bug Reports & Suggestions

Found a bug or have a feature request?

- **GitHub Issues:** [Create Issue](https://github.com/AllFiRE0/CoreProtectionAssistant/issues)
- **Discord:** AllF1RE#0000

---

**Made with ❤️ by AllF1RE**
```

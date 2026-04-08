Вот **обновлённый `README.md`** с добавлением всех нововведений v1.1.0 в раздел **Features**:

```markdown
# CoreProtectionAssistant

[![Build Status](https://img.shields.io/github/actions/workflow/status/AllFiRE0/CoreProtectionAssistant/build.yml)](https://github.com/AllFiRE0/CoreProtectionAssistant/actions)
[![Version](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/AllFiRE0/CoreProtectionAssistant/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)

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
- **Apologies Tracking** — counts apologies and calculates repentance ratio (apologies / violations)
- **Report System** with anti-abuse protection
- Automatic violator analysis based on playtime, warnings, and report count
- **Same IP & Alt Detection** — detects players with same IP and similar nicknames
- **Prohibited Permissions Check** — alerts when staff members have dangerous permissions
- **Grief Detection** — detects when a player breaks blocks previously modified by others (NEW)
- **Similar Nickname Detection** — `1=i=l`, `0=o`, `5=s=6`, etc. (NEW)

### 🤖 ChatBot (NEW in v1.1.0)
- Fully configurable chatbot that responds to player messages
- **Rule-based system** with priorities, permissions, and cooldowns
- **Condition support** — use PAPI placeholders with comparisons (`>`, `<`, `==`, `contains`, `AND`, `OR`)
- **Symbol prefix** — respond only to messages starting with `!`, `?`, etc.
- **Random responses** — choose from a list of possible answers
- **Delayed responses** — bot can wait before replying
- **Chance-based triggers** — probability of response in percentage
- **Context variables** — `%player_name%`, `%player_world%`, `%player_time%`, `%target%`, `%message%`
- All command prefixes supported: `message!`, `broadcast!`, `asConsole!`, `sound!`, `title!`, `actionbar!`
- Configuration file: `chatbot.yml`

### ⏱️ Temporary Warnings & Silent Mode (NEW)
- **Temporary warnings** — `-t:1d` (day), `-t:1h` (hour), `-t:1m` (minute), `-t:1s` (second)
- **Silent mode** — `-s` flag suppresses all command output
- Example: `/cpa warn Player Griefing -t:20m -s`

### Statistics & Integration
- **Direct CoreProtect database reading** — no duplicate storage (NEW)
- CoreProtect integration (via reflection — no hard dependency)
- PlaceholderAPI support (35+ placeholders)
- **New placeholders** — `%cpa_player_apologies_count%`, `%cpa_player_violations_apologies_ratio%`
- SQLite (with HikariCP connection pool) and MySQL support
- Optional integrations: CMI, LuckPerms

### Quality of Life
- **Full localization** — all messages in `lang.yml` (supports HEX colors `&#RRGGBB`)
- **Message toggling** — disable any message by setting it to `""` or `"none"`
- **Offline player tab-complete** — all commands suggest offline players (up to 20)
- **Centralized reload** — `/cpa reload` reloads all configurations including ChatBot
- **Debug mode** — `debug: true/false` in config for troubleshooting (NEW)

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

## ⚙️ Configuration

### Main Config (`config.yml`)
```yaml
database:
  type: sqlite  # or mysql

debug: false  # Enable for troubleshooting

tracked_moder_commands:
  - give
  - gamemode
  - ban
  # ...

abuse_weights:
  ban_without_reason: 10
  self_give_items: 8
  griefing: 10  # NEW
  # ...

grief_detection:  # NEW
  enabled: true
  tracked_blocks:
    - CHEST
    - FURNACE
    # ...
  grief_commands:
    - "asConsole! cpa warn %player_name% Griefing detected -t:20m -s"
```

### Chat Rules (`chattrules.yml`)
```yaml
rules:
  severe_insult:
    priority: 100
    regex: "(?i)(fyou|motherlover)"
    action: "punish"
    punishment: "ban"
    duration_ticks: 6048000
    
  simple_apology:
    priority: 15
    regex: "(?i)(sorry|my bad)"
    action: "apology"
    warnings_clear: 1
```

### ChatBot (`chatbot.yml`) — NEW
```yaml
enabled: true
permission_usage: ""
debug: false

rules:
  greeting:
    priority: 100
    regex: "(?i)(привет|hello|hi)"
    symbol: ""
    answer_cmds:
      - "message! &a&lБОТ &7» &fПривет, %player_name%!"
    answer_cmds_random:
      - "message! &a&lБОТ &7» &fЗдарова!"
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
| `/cpa top <type>` | `cpa.moder` | View leaderboards |
| `/cpa check <player>` | `cpa.moder` | Quick player check |
| `/cpa warn <player> [reason] [-t:time] [-s]` | `cpa.warn` | Issue warning (NEW flags) |
| `/cpa warn clear <player> <amount> [-s]` | `cpa.warn.clear` | Clear warnings (NEW -s flag) |
| `/cpa warn list <player> [-s]` | `cpa.moder` | List active warnings (NEW -s flag) |
| `/cpa reload` | `cpa.reload` | Reload all configurations |
| `/cpa report <player> <reason>` | `cpa.report` | Report a player (staff) |
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
| `%cpa_player_commands_count%` | Total commands used |
| `%cpa_player_deaths%` | Total deaths |
| `%cpa_player_kills%` | Total kills |
| `%cpa_player_first_seen%` | First join date |
| `%cpa_player_last_seen%` | Last seen date |
| `%cpa_player_warnings_count%` | Active warnings |
| `%cpa_player_violations_count%` | Chat violations |
| `%cpa_player_apologies_count%` | Total apologies (NEW) |
| `%cpa_player_violations_apologies_ratio%` | Repentance ratio (NEW) |
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

Supports both **SQLite** (with HikariCP connection pool) and **MySQL**.

### Tables Created
- `cpa_command_logs` — All tracked commands
- `cpa_staff_actions` — Staff activity log
- `cpa_warnings` — Player and staff warnings
- `cpa_reports` — Player reports
- `cpa_chat_violations` — Chat filter violations
- `cpa_apologies` — Apology history
- `cpa_abuse_scores` — Staff abuse scores
- `cpa_grief_actions` — Grief detection log (NEW)

---

## 🔨 Building from Source

```bash
git clone https://github.com/AllFiRE0/CoreProtectionAssistant.git
cd CoreProtectionAssistant
mvn clean package
# Output: target/CoreProtectionAssistant-1.1.0.jar
```

Windows: run `build.bat`

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

## 🙏 Acknowledgements

- [PaperMC](https://papermc.io/) — Server software
- [CoreProtect](https://www.coreprotect.net/) — Block logging
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — Placeholder support

---

**Made with ❤️ by AllF1RE**
```

Готово! Теперь `README.md` полностью отражает все нововведения v1.1.0.

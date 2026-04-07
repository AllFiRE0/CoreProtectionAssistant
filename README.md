# CoreProtectionAssistant

Advanced staff monitoring and player protection system for Minecraft servers.

## Author
**AllF1RE**

## Features
- Staff action monitoring (bans, mutes, kicks, give commands)
- Abuse score system for staff members
- Unified chat rules with regex support
- Player reporting system with anti-abuse
- Warning system for players and staff
- CoreProtect integration (via reflection)
- PlaceholderAPI support
- SQLite and MySQL support

## Requirements
- Paper/Leaf 1.20.4+
- Java 21+
- CoreProtect (optional but recommended)
- PlaceholderAPI (optional)
- LuckPerms (optional)

## Commands
- `/cpa stats <player>` - View player statistics
- `/cpa staff <player>` - View staff statistics
- `/cpa top <type>` - View leaderboards
- `/cpa check <player>` - Quick player check
- `/cpa warn <player> [reason]` - Issue warning
- `/cpa warn clear <player> <amount>` - Clear warnings
- `/cpa reload` - Reload configuration
- `/report <player> <reason>` - Report a player

## Permissions
- `cpa.moder` - Access to player statistics
- `cpa.staff` - Access to staff statistics
- `cpa.warn` - Can issue warnings
- `cpa.warn.clear` - Can clear warnings
- `cpa.report` - Can use /report
- `cpa.report.see` - Can see reports
- `cpa.reload` - Can reload config
- `cpa.bypass.chat` - Bypass chat filter

## Placeholders
- `%cpa_player_blocks_broken%`
- `%cpa_player_warnings_count%`
- `%cpa_player_violations_count%`
- `%cpa_staff_abuse_score%`
- `%cpa_staff_bans_count%`

## Building
```bash
mvn clean package

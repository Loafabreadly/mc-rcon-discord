# Minecraft RCON Discord Bot

A Java 21 Discord bot that integrates with Minecraft servers via RCON to manage server whitelists through Discord slash commands. Built for Docker deployment alongside Minecraft servers.

## Features

- 🎮 **Whitelist Management**: Users can request to be added to the Minecraft server whitelist via Discord
- ⚡ **RCON Integration**: Direct communication with Minecraft server through RCON protocol
- 🔐 **Permission System**: Role-based access control for bot commands
- ⏱️ **Cooldown System**: Prevents spam with configurable cooldown periods
- 🛡️ **Input Validation**: Validates Minecraft usernames and prevents duplicate entries
- 📊 **Server Status**: Check server status, player count, and TPS
- 🐳 **Docker Ready**: Containerized application with docker-compose setup
- 📝 **Comprehensive Logging**: Structured logging with file rotation

## Commands

- `/whitelist <username>` - Request to add a Minecraft username to the server whitelist
- `/server-status` - Display current server status and online players
- `/ping` - Test bot responsiveness

## Prerequisites

- Docker and Docker Compose
- Discord Bot Token
- Minecraft Server with RCON enabled

## Quick Start

### 1. Create Discord Bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to the "Bot" section
4. Create a bot and copy the token
5. Enable the following bot permissions:
   - Send Messages
   - Use Slash Commands
   - Read Message History

### 2. Setup Environment

1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd mc-rcon-discord
   ```

2. Copy the environment template:
   ```bash
   cp .env.example .env
   ```

3. Edit `.env` with your configuration:
   ```bash
   # Required settings
   DISCORD_TOKEN=your_discord_bot_token_here
   MINECRAFT_RCON_PASSWORD=your_secure_rcon_password_here
   
   # Optional settings
   DISCORD_GUILD_ID=your_discord_server_id
   DISCORD_ALLOWED_ROLES=Admin,Moderator
   ```

### 3. Deploy with Docker Compose

```bash
# Start the entire stack (bot + minecraft server)
docker-compose up -d

# Or just the bot (if you have an existing Minecraft server)
docker-compose up -d mc-rcon-bot

# View logs
docker-compose logs -f mc-rcon-bot
```

### 4. Invite Bot to Discord

Generate an invite URL with the following permissions:
- `applications.commands` (Slash Commands)
- `bot` with "Send Messages" and "Use Slash Commands"

Example URL:
```
https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_CLIENT_ID&permissions=2147483648&scope=bot%20applications.commands
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DISCORD_TOKEN` | ✅ | - | Discord bot token |
| `DISCORD_GUILD_ID` | ❌ | - | Discord server ID (for faster command updates) |
| `DISCORD_ALLOWED_ROLES` | ❌ | - | Comma-separated list of roles allowed to use commands |
| `MINECRAFT_RCON_HOST` | ❌ | `minecraft-server` | Minecraft server hostname |
| `MINECRAFT_RCON_PORT` | ❌ | `25575` | RCON port |
| `MINECRAFT_RCON_PASSWORD` | ✅ | - | RCON password |
| `MINECRAFT_RCON_TIMEOUT` | ❌ | `10` | RCON connection timeout (seconds) |
| `BOT_COOLDOWN_MINUTES` | ❌ | `60` | Cooldown between whitelist requests |
| `BOT_MAX_USERNAME_LENGTH` | ❌ | `16` | Maximum Minecraft username length |
| `BOT_ENABLE_DUPLICATE_CHECK` | ❌ | `true` | Check if player is already whitelisted |

### Configuration File (Alternative)

Instead of environment variables, you can create a `config.json` file:

```json
{
  "discord": {
    "token": "your_discord_bot_token",
    "guild_id": "your_guild_id",
    "allowed_roles": ["Admin", "Moderator"]
  },
  "minecraft": {
    "host": "minecraft-server",
    "port": 25575,
    "password": "your_rcon_password",
    "timeout_seconds": 10
  },
  "bot": {
    "whitelist_cooldown_minutes": 60,
    "max_username_length": 16,
    "enable_duplicate_check": true
  }
}
```

## Minecraft Server Setup

### Enable RCON

Add these settings to your `server.properties`:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=your_secure_password_here
```

### Enable Whitelist

```properties
white-list=true
enforce-whitelist=true
```

### Using the Provided Docker Compose

The included `docker-compose.yml` sets up a complete Minecraft server with RCON enabled. Customize the environment variables as needed.

## Development

### Building from Source

```bash
# Clone repository
git clone <repository-url>
cd mc-rcon-discord

# Build with Maven
mvn clean package

# Run locally
java -jar target/mc-rcon-discord-1.0.0.jar
```

### Project Structure

```
src/
├── main/java/com/example/mcrcon/
│   ├── MinecraftRconBot.java           # Main application class
│   ├── config/
│   │   ├── BotConfig.java              # Configuration data classes
│   │   └── ConfigManager.java          # Configuration loader
│   ├── service/
│   │   └── MinecraftRconService.java   # RCON communication
│   ├── commands/
│   │   └── WhitelistCommand.java       # Discord command handlers
│   └── rcon/
│       └── RconClient.java             # Custom RCON client
└── main/resources/
    └── logback.xml                     # Logging configuration
```

## Troubleshooting

### Common Issues

**Bot not responding to commands:**
- Verify the bot has proper permissions in your Discord server
- Check if commands are registered (may take up to 1 hour for global commands)
- Use `DISCORD_GUILD_ID` for faster command updates during development

**RCON connection failed:**
- Ensure RCON is enabled in `server.properties`
- Verify the RCON password matches
- Check if the Minecraft server is running and accessible
- Ensure port 25575 is not blocked by firewall

**Permission denied errors:**
- Configure `DISCORD_ALLOWED_ROLES` to restrict command usage
- Ensure users have the required roles

### Logs

Check application logs:
```bash
# Docker logs
docker-compose logs -f mc-rcon-bot

# Local logs
tail -f logs/mc-rcon-bot.log
```

### Testing RCON Connection

You can test RCON connectivity manually:
```bash
# Using mcrcon tool
mcrcon -H minecraft-server -P 25575 -p your_password "list"

# Using telnet (basic test)
telnet minecraft-server 25575
```

## Security Considerations

- Keep your Discord bot token secure
- Use a strong RCON password
- Restrict bot permissions to necessary roles
- Regular security updates for Docker images
- Consider network isolation for production deployments

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review logs for error messages
3. Open an issue on GitHub with:
   - Error logs
   - Configuration (without sensitive data)
   - Steps to reproduce

---

**Note**: This bot is designed to work with vanilla Minecraft servers and most popular server software (Paper, Spigot, etc.) that support standard RCON commands.
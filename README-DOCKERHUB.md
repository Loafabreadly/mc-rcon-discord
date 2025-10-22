# Minecraft RCON Discord Bot

![Docker Pulls](https://img.shields.io/docker/pulls/loafabreadly/mc-rcon-discord)
![Docker Image Size](https://img.shields.io/docker/image-size/loafabreadly/mc-rcon-discord/latest)
![GitHub License](https://img.shields.io/github/license/loafabreadly/mc-rcon-discord)

A comprehensive Discord bot for managing Minecraft servers via RCON, built with Java 21 and designed for Docker deployment.

## 🎮 Features

### **Core Functionality**
- **Whitelist Management**: Players can request to be added to the server whitelist via Discord commands
- **Persistent Status Pages**: Auto-updating status displays that survive bot restarts
- **Server Monitoring**: Real-time server status, player count, and performance metrics
- **Admin Controls**: Complete server administration through Discord with separate admin roles
- **Permission System**: Role-based access control for sensitive commands
- **Smart Validation**: Username validation and duplicate checking

### **Discord Commands**

#### **User Commands**
- `/whitelist <username>` - Request whitelist access
- `/server-status` - Check server status and performance
- `/players` - View online players list
- `/help` - Show command help
- `/ping` - Test bot responsiveness

#### **Status Page Commands** (Require configured roles)
- `/status-page create` - Create persistent status page that updates every 5 minutes
- `/status-page remove` - Remove status page from current channel

#### **Admin Commands** (Require `DISCORD_ADMIN_ROLE` or fallback to allowed roles)
- `/admin whitelist-info` - View complete whitelist
- `/admin whitelist-remove <username>` - Remove player from whitelist
- `/admin console <command>` - Execute server console commands

## 🚀 Quick Start

### Using Docker Compose (Recommended)

1. **Create docker-compose.yml:**
```yaml
version: '3.8'

services:
  # Minecraft RCON Discord Bot
  mc-rcon-bot:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: mc-rcon-discord-bot
    restart: unless-stopped
    
    environment:
      # Discord Configuration
      - DISCORD_TOKEN=
      - DISCORD_GUILD_ID=
      - DISCORD_ALLOWED_ROLES=Minecrafter
      - DISCORD_ADMIN_ROLE=Admin
      
      # Minecraft RCON Configuration
      - MINECRAFT_RCON_HOST=
      - MINECRAFT_RCON_PORT=25575
      - MINECRAFT_RCON_PASSWORD=
      - MINECRAFT_RCON_TIMEOUT=10

      # Bot Settings
      - BOT_COOLDOWN_MINUTES=60
      - BOT_MAX_USERNAME_LENGTH=16
      - BOT_ENABLE_DUPLICATE_CHECK=true

    volumes:
      # Mount logs directory for persistence
      - ./logs:/app/logs
      # Mount config directory for optional config file
      #- ./config:/app/config

    
    # Health check
    healthcheck:
      test: ["CMD-SHELL", "ps aux | grep '[j]ava' || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    depends_on:
      - mc
  mc:
    image: itzg/minecraft-server:latest
    tty: true
    stdin_open: true
    ports:
      - "25565:25565" #Game Port
      - "25575:25575" #RCON Port
    environment:
      EULA: "TRUE"
      MEMORY: "3072M"
    volumes:
      - "./data:/data"            
```

2. **Start the bot:**
```bash
docker-compose up -d
```

### Using Docker Run

```bash
docker run -d \
  --name mc-rcon-bot \
  --restart unless-stopped \
  -e DISCORD_TOKEN=your_token \
  -e MINECRAFT_RCON_HOST=your_server \
  -e MINECRAFT_RCON_PASSWORD=your_password \
  -v ./logs:/app/logs \
  yourusername/minecraft-rcon-discord-bot:latest
```

## ⚙️ Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DISCORD_TOKEN` | ✅ | - | Discord bot token |
| `DISCORD_GUILD_ID` | ❌ | - | Discord server ID (for faster command updates) |
| `DISCORD_ALLOWED_ROLES` | ❌ | - | Comma-separated list of roles for basic commands |
| `DISCORD_ADMIN_ROLE` | ❌ | - | Specific role for admin commands (takes precedence) |
| `MINECRAFT_RCON_HOST` | ❌ | `localhost` | Minecraft server hostname |
| `MINECRAFT_RCON_PORT` | ❌ | `25575` | RCON port |
| `MINECRAFT_RCON_PASSWORD` | ✅ | - | RCON password |
| `MINECRAFT_RCON_TIMEOUT` | ❌ | `10` | RCON connection timeout (seconds) |
| `BOT_COOLDOWN_MINUTES` | ❌ | `60` | Cooldown between whitelist requests |
| `BOT_MAX_USERNAME_LENGTH` | ❌ | `16` | Maximum Minecraft username length |
| `BOT_ENABLE_DUPLICATE_CHECK` | ❌ | `true` | Check for existing whitelist entries |

### Alternative: JSON Configuration

Create a `config.json` file and mount it to `/app/config.json`:

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

## 🔧 Minecraft Server Setup

### Enable RCON

Add to your `server.properties`:
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

### Restart your Minecraft server for changes to take effect.

## � Persistent Status Pages

**New in v1.0**: Create auto-updating status displays that survive bot restarts!

- **Create**: `/status-page create` - Creates a status message that updates every 5 minutes
- **Remove**: `/status-page remove` - Removes the status page from current channel
- **Persistent**: Status pages automatically restore after bot restarts
- **Real-time**: Shows server status, TPS, player count, and online players
- **Smart**: One status page per channel, automatic cleanup if messages are deleted

Perfect for server information channels where you want live server data without chat spam.

## �🐳 Docker Tags

- `latest` - Latest stable release
- `v1.x.x` - Specific version tags
- `main` - Latest development build

## 📊 Health Monitoring

The container includes health checks and structured logging:

```bash
# Check container health
docker ps

# View logs
docker logs mc-rcon-bot

# View log files (if volume mounted)
tail -f logs/mc-rcon-bot.log
```

## 🔒 Security

- **Non-root container**: Runs as dedicated user for security
- **Command filtering**: Dangerous console commands are blocked
- **Role-based access**: Admin commands require Discord role permissions
- **Input validation**: All user inputs are validated and sanitized
- **Secure defaults**: Minimal permissions and secure configuration

## 🏗️ Advanced Deployment

### With Existing Minecraft Server Stack

```yaml
version: '3.8'
services:
  minecraft:
    image: itzg/minecraft-server:java21
    # ... your existing minecraft config
    
  mc-rcon-bot:
    image: yourusername/minecraft-rcon-discord-bot:latest
    depends_on:
      - minecraft
    environment:
      - MINECRAFT_RCON_HOST=minecraft
      # ... other config
    networks:
      - minecraft-network

networks:
  minecraft-network:
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mc-rcon-bot
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mc-rcon-bot
  template:
    metadata:
      labels:
        app: mc-rcon-bot
    spec:
      containers:
      - name: bot
        image: yourusername/minecraft-rcon-discord-bot:latest
        env:
        - name: DISCORD_TOKEN
          valueFrom:
            secretKeyRef:
              name: discord-secret
              key: token
        # ... other env vars
```

## 🛠️ Development

### Building from Source

```bash
git clone https://github.com/yourusername/minecraft-rcon-discord-bot.git
cd minecraft-rcon-discord-bot
./build.sh
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/minecraft-rcon-discord-bot/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/minecraft-rcon-discord-bot/discussions)
- **Documentation**: [Wiki](https://github.com/yourusername/minecraft-rcon-discord-bot/wiki)

## 🌟 Acknowledgments

- Built with [JDA (Java Discord API)](https://github.com/DV8FromTheWorld/JDA)
- Inspired by the Minecraft server administration community
- Thanks to all contributors and users

---

**Ready to enhance your Minecraft server management? Deploy the bot today!**
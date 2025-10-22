#!/bin/bash

# Build script for Minecraft RCON Discord Bot

set -e

echo "🚀 Building Minecraft RCON Discord Bot..."

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Create necessary directories
mkdir -p logs config

# Copy environment file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env
    echo "⚠️  Please edit .env with your configuration before running!"
fi

# Build Docker image
echo "🔨 Building Docker image..."
docker-compose build mc-rcon-bot

echo "✅ Build completed successfully!"
echo ""
echo "Next steps:"
echo "1. Edit .env with your Discord bot token and RCON password"
echo "2. Run: docker-compose up -d"
echo "3. Check logs: docker-compose logs -f mc-rcon-bot"
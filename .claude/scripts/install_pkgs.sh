#!/bin/bash

# Only run in remote environments (Claude Code Web)
if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then
  exit 0
fi

# Apply offline repository configuration to build.gradle if not already present
if ! grep -q "apply from: '.claude/offline-repositories.gradle'" build.gradle; then
  echo "🔧 Configuring offline Gradle repositories..."
  # Insert after the plugins block (after the closing brace)
  sed -i '/^plugins {/,/^}/a\\
\
apply from: '\''.claude/offline-repositories.gradle'\''' build.gradle
  echo "✅ Offline repositories configured"
fi
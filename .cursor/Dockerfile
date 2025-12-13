FROM ubuntu:24.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Essential tools
    curl \
    wget \
    zip \
    unzip \
    git \
    ca-certificates \
    gnupg \
    # Build tools
    build-essential \
    # SQLite (project uses SQLite for block index)
    sqlite3 \
    libsqlite3-dev \
    # JavaFX meta-package (includes all GTK, X11, OpenGL dependencies)
    openjfx \
    # Fonts for text rendering
    fonts-dejavu-core \
    fonts-liberation \
    fontconfig \
    # Headless GUI testing
    xvfb \
    x11-utils \
    # Useful utilities
    tree \
    jq \
    vim-tiny \
    less \
    htop \
    procps \
    # Create user
    sudo \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js (required for MCP servers using npx)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -s /bin/bash ubuntu && \
    echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

# Install SDKMAN for ubuntu user
ENV SDKMAN_DIR="/home/ubuntu/.sdkman"
USER ubuntu
WORKDIR /home/ubuntu

RUN curl -s "https://get.sdkman.io" | bash

# Configure shell to source SDKMAN automatically
RUN echo 'source "$SDKMAN_DIR/bin/sdkman-init.sh"' >> /home/ubuntu/.bashrc

# Copy .sdkmanrc to pre-install Java (will be overwritten by Cursor mount)
COPY --chown=ubuntu:ubuntu .sdkmanrc /home/ubuntu/.sdkmanrc

# Install Java from .sdkmanrc
RUN bash -c "source $SDKMAN_DIR/bin/sdkman-init.sh && sdk env install"

# Set up environment for Java
# Capture current PATH and prepend Java bin directory
# ENV doesn't reliably expand $PATH from base image, so we use RUN to get the actual value
RUN export JAVA_BIN="$SDKMAN_DIR/candidates/java/current/bin" && \
    export CURRENT_PATH=$(printenv PATH || echo "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin") && \
    echo "export PATH=\"$JAVA_BIN:$CURRENT_PATH\"" >> /home/ubuntu/.bashrc && \
    echo "export JAVA_HOME=\"$SDKMAN_DIR/candidates/java/current\"" >> /home/ubuntu/.bashrc
ENV JAVA_HOME="$SDKMAN_DIR/candidates/java/current"
# Set PATH explicitly with standard Linux paths (ENV doesn't expand $PATH from base image)
ENV PATH="$SDKMAN_DIR/candidates/java/current/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# Verify Java installation
RUN bash -c "source $SDKMAN_DIR/bin/sdkman-init.sh && java -version"

# Keep .sdkmanrc for sdk env command (Cursor will mount the real one at runtime, overriding this)
# Note: environment.json runs "sdk env" which requires .sdkmanrc to be present


#!/bin/bash
# =============================================================================
# Prepare Compose Stage - Port dari PrepareComposeStage.java
# =============================================================================
# Generates docker-compose.yml and .env file for deployment
#
# Usage:
#   ./prepare-compose.sh --extracted-path <path> --slug <slug> --env-file <path> \
#                        [--has-user-compose] [--frontend-dist <path>] [--backend-dist <path>]
#
# Arguments:
#   --extracted-path     Path to extracted artifact directory (required)
#   --slug               Project slug (required)
#   --env-file           Path to env file (required)
#   --has-user-compose   Flag if user provided docker-compose.yml (optional)
#   --frontend-dist      Path to frontend dist (optional)
#   --backend-dist       Path to backend dist (optional)
#
# Output:
#   COMPOSE_PATH   - Path to generated docker-compose.yml
#   NETWORK_NAME   - Name of the Docker network
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[PREPARE-COMPOSE]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[PREPARE-COMPOSE]${NC} $1"
}

log_error() {
    echo -e "${RED}[PREPARE-COMPOSE]${NC} $1" >&2
}

# ============================================================================
# Naming Utils (ported from NamingUtils.java)
# ============================================================================

container_name() {
    local service=$1
    local slug=$2
    echo "prices-${slug}-${service}"
}

network_name() {
    local slug=$1
    echo "prices-${slug}-network"
}

volume_name() {
    local volume=$1
    local slug=$2
    echo "prices-${slug}_${volume}"
}

# ============================================================================
# Parse Arguments
# ============================================================================

EXTRACTED_PATH=""
SLUG=""
ENV_FILE=""
HAS_USER_COMPOSE=false
FRONTEND_DIST=""
BACKEND_DIST=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --extracted-path)
            EXTRACTED_PATH="$2"
            shift 2
            ;;
        --slug)
            SLUG="$2"
            shift 2
            ;;
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --has-user-compose)
            HAS_USER_COMPOSE=true
            shift
            ;;
        --frontend-dist)
            FRONTEND_DIST="$2"
            shift 2
            ;;
        --backend-dist)
            BACKEND_DIST="$2"
            shift 2
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$EXTRACTED_PATH" ]]; then
    log_error "Missing required argument: --extracted-path"
    exit 1
fi

if [[ -z "$SLUG" ]]; then
    log_error "Missing required argument: --slug"
    exit 1
fi

if [[ -z "$ENV_FILE" ]]; then
    log_error "Missing required argument: --env-file"
    exit 1
fi

# ============================================================================
# Generate docker-compose.yml
# ============================================================================

COMPOSE_PATH="${EXTRACTED_PATH}/docker-compose.yml"
NETWORK_NAME=$(network_name "$SLUG")

if [[ "$HAS_USER_COMPOSE" == true ]]; then
    log_info "User docker-compose.yml found, customizing volumes/networks..."
    # TODO: Implement user compose customization (yaml parsing)
    # For now we assume generated compose
else
    log_info "Generating docker-compose.yml..."
    
    POSTGRES_CONTAINER=$(container_name "postgres" "$SLUG")
    BACKEND_CONTAINER=$(container_name "backend" "$SLUG")
    FRONTEND_CONTAINER=$(container_name "frontend" "$SLUG")
    POSTGRES_VOLUME=$(volume_name "postgres_data" "$SLUG")
    
    # Generate compose file
    cat > "$COMPOSE_PATH" << EOF
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: ${POSTGRES_CONTAINER}
    environment:
      POSTGRES_DB: ${SLUG}
      POSTGRES_USER: \${AMANAH_DB_USERNAME}
      POSTGRES_PASSWORD: \${AMANAH_DB_PASSWORD}
    volumes:
      - ${POSTGRES_VOLUME}:/var/lib/postgresql/data
    networks:
      - ${NETWORK_NAME}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \${AMANAH_DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  backend:
    build:
      context: ./backend-dist
      dockerfile: Dockerfile
    image: ${BACKEND_CONTAINER}:latest
    container_name: ${BACKEND_CONTAINER}
    env_file:
      - .env
    networks:
      - ${NETWORK_NAME}
      - prices-proxy-network
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

  frontend:
    build:
      context: ./frontend-dist
      dockerfile: Dockerfile
EOF

    # Add build args for frontend if env file has VITE_* or REACT_APP_* vars
    if [[ -f "$ENV_FILE" ]]; then
        frontend_vars=$(grep -E "^(VITE_|REACT_APP_)" "$ENV_FILE" 2>/dev/null || true)
        if [[ -n "$frontend_vars" ]]; then
            echo "      args:" >> "$COMPOSE_PATH"
            while IFS='=' read -r key value || [[ -n "$key" ]]; do
                [[ -z "$key" ]] && continue
                echo "        ${key}: \${${key}}" >> "$COMPOSE_PATH"
            done <<< "$frontend_vars"
        fi
    fi

    cat >> "$COMPOSE_PATH" << EOF
    image: ${FRONTEND_CONTAINER}:latest
    container_name: ${FRONTEND_CONTAINER}
    networks:
      - ${NETWORK_NAME}
      - prices-proxy-network
    restart: unless-stopped

networks:
  ${NETWORK_NAME}:
    driver: bridge
  prices-proxy-network:
    external: true

volumes:
  ${POSTGRES_VOLUME}:
EOF

    log_info "Generated compose file: ${COMPOSE_PATH}"
fi

# ============================================================================
# Copy .env file to extracted path
# ============================================================================

if [[ -f "$ENV_FILE" ]]; then
    ENV_DEST="${EXTRACTED_PATH}/.env"
    cp "$ENV_FILE" "$ENV_DEST"
    env_count=$(wc -l < "$ENV_FILE" | tr -d ' ')
    log_info "Copied .env file with ${env_count} variables"
fi

# ============================================================================
# Output
# ============================================================================

log_info "Prepare compose stage completed successfully"
echo ""
echo "COMPOSE_PATH=${COMPOSE_PATH}"
echo "NETWORK_NAME=${NETWORK_NAME}"

#!/bin/bash
# =============================================================================
# Docker Run Stage - Port dari DockerRunStage.java
# =============================================================================
# Stops existing containers and builds/starts new ones
#
# Usage:
#   ./docker-run.sh --compose-path <path> --slug <slug> [--redeploy]
#
# Arguments:
#   --compose-path   Path to docker-compose.yml (required)
#   --slug           Project slug (required)
#   --redeploy       Preserve volumes on down (optional)
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
#   2 - Docker compose failed
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[DOCKER-RUN]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[DOCKER-RUN]${NC} $1"
}

log_error() {
    echo -e "${RED}[DOCKER-RUN]${NC} $1" >&2
}

# ============================================================================
# Detect Docker Compose Command
# ============================================================================

detect_docker_compose_cmd() {
    if docker compose version &>/dev/null; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

# ============================================================================
# Naming Utils
# ============================================================================

project_name() {
    local slug=$1
    echo "prices-${slug}"
}

# ============================================================================
# Parse Arguments
# ============================================================================

COMPOSE_PATH=""
SLUG=""
REDEPLOY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --compose-path)
            COMPOSE_PATH="$2"
            shift 2
            ;;
        --slug)
            SLUG="$2"
            shift 2
            ;;
        --redeploy)
            REDEPLOY=true
            shift
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$COMPOSE_PATH" ]]; then
    log_error "Missing required argument: --compose-path"
    exit 1
fi

if [[ -z "$SLUG" ]]; then
    log_error "Missing required argument: --slug"
    exit 1
fi

# ============================================================================
# Setup
# ============================================================================

DOCKER_COMPOSE_CMD=$(detect_docker_compose_cmd)
PROJECT_NAME=$(project_name "$SLUG")
COMPOSE_DIR=$(dirname "$COMPOSE_PATH")

log_info "Using docker compose: ${DOCKER_COMPOSE_CMD}"
log_info "Project name: ${PROJECT_NAME}"

# ============================================================================
# 1. Stop existing containers if any
# ============================================================================

log_info "Stopping existing containers if any..."

DOWN_ARGS="-f ${COMPOSE_PATH} -p ${PROJECT_NAME} down"
if [[ "$REDEPLOY" == false ]]; then
    DOWN_ARGS="${DOWN_ARGS} -v"
fi

# Run down command, ignore errors (containers might not exist)
cd "$COMPOSE_DIR"
$DOCKER_COMPOSE_CMD -f "$COMPOSE_PATH" -p "$PROJECT_NAME" down ${REDEPLOY:+} $( [[ "$REDEPLOY" == false ]] && echo "-v" ) 2>&1 | while read -r line; do
    [[ -n "$line" ]] && log_info "$line"
done || true

# ============================================================================
# 2. Build and start containers
# ============================================================================

log_info "Building and starting containers from: ${COMPOSE_PATH}"

cd "$COMPOSE_DIR"
if ! $DOCKER_COMPOSE_CMD -f "$COMPOSE_PATH" -p "$PROJECT_NAME" up -d --build 2>&1 | while read -r line; do
    [[ -n "$line" ]] && log_info "$line"
done; then
    log_error "Docker compose up failed"
    exit 2
fi

# ============================================================================
# Output
# ============================================================================

log_info "Docker run stage completed successfully"
echo ""
echo "PROJECT_NAME=${PROJECT_NAME}"

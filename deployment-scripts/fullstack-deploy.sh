#!/bin/bash
# =============================================================================
# Fullstack Deploy - Main Orchestrator
# =============================================================================
# Orchestrates the full deployment pipeline by calling individual stage scripts
#
# Usage:
#   ./fullstack-deploy.sh --artifact <path> --slug <slug> [options]
#
# Required Arguments:
#   --artifact              Path to artifact zip file
#   --slug                  Project slug (unique identifier)
#
# Optional Arguments:
#   --frontend-url          Frontend URL (default: <slug>.<PRICES_DOMAIN>)
#   --backend-url           Backend URL (default: backend-<slug>.<PRICES_DOMAIN>)
#   --custom-frontend-url   Custom frontend URL
#   --custom-backend-url    Custom backend URL
#   --monitoring-url        Monitoring URL (default: monitoring-<slug>.<PRICES_DOMAIN>)
#   --custom-monitoring-url Custom monitoring URL
#   --expose-monitoring     Expose monitoring endpoints
#   --env-file              Path to .env file with additional env vars
#   --env KEY=VALUE         Additional env var (can be repeated)
#   --redeploy              Preserve volumes on redeploy
#   --dry-run               Show what would be done without executing
#
# Environment Variables:
#   PRICES_DOMAIN           Parent domain (default: skripsi.isacitra.com)
#   PRICES_DEPLOYMENTS_DIR  Deployments directory (default: /var/prices/deployments)
#   PRICES_NGINX_CONFIG_DIR Nginx config directory (default: /var/prices/nginx/conf.d)
#   PRICES_NGINX_CONTAINER  Nginx container name (default: prices-nginx)
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
#   2-9 - Stage-specific errors
# =============================================================================

set -euo pipefail

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[DEPLOY]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[DEPLOY]${NC} $1"
}

log_error() {
    echo -e "${RED}[DEPLOY]${NC} $1" >&2
}

log_stage() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}  Stage: $1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

# ============================================================================
# Default Values
# ============================================================================

PRICES_DOMAIN="${PRICES_DOMAIN:-skripsi.isacitra.com}"

# ============================================================================
# Parse Arguments
# ============================================================================

ARTIFACT=""
SLUG=""
FRONTEND_URL=""
BACKEND_URL=""
CUSTOM_FRONTEND_URL=""
CUSTOM_BACKEND_URL=""
MONITORING_URL=""
CUSTOM_MONITORING_URL=""
EXPOSE_MONITORING=false
INPUT_ENV_FILE=""
REDEPLOY=false
DRY_RUN=false
declare -a EXTRA_ENV_VARS=()

while [[ $# -gt 0 ]]; do
    case $1 in
        --artifact)
            ARTIFACT="$2"
            shift 2
            ;;
        --slug)
            SLUG="$2"
            shift 2
            ;;
        --frontend-url)
            FRONTEND_URL="$2"
            shift 2
            ;;
        --backend-url)
            BACKEND_URL="$2"
            shift 2
            ;;
        --custom-frontend-url)
            CUSTOM_FRONTEND_URL="$2"
            shift 2
            ;;
        --custom-backend-url)
            CUSTOM_BACKEND_URL="$2"
            shift 2
            ;;
        --monitoring-url)
            MONITORING_URL="$2"
            shift 2
            ;;
        --custom-monitoring-url)
            CUSTOM_MONITORING_URL="$2"
            shift 2
            ;;
        --expose-monitoring)
            EXPOSE_MONITORING=true
            shift
            ;;
        --env-file)
            INPUT_ENV_FILE="$2"
            shift 2
            ;;
        --env)
            EXTRA_ENV_VARS+=("--env" "$2")
            shift 2
            ;;
        --redeploy)
            REDEPLOY=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            head -50 "$0" | tail -45
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# ============================================================================
# Validate Required Arguments
# ============================================================================

if [[ -z "$ARTIFACT" ]]; then
    log_error "Missing required argument: --artifact"
    echo "Usage: $0 --artifact <path> --slug <slug> [options]"
    exit 1
fi

if [[ -z "$SLUG" ]]; then
    log_error "Missing required argument: --slug"
    echo "Usage: $0 --artifact <path> --slug <slug> [options]"
    exit 1
fi

if [[ ! -f "$ARTIFACT" ]]; then
    log_error "Artifact file not found: $ARTIFACT"
    exit 1
fi

# ============================================================================
# Apply Defaults
# ============================================================================

[[ -z "$FRONTEND_URL" ]] && FRONTEND_URL="${SLUG}.${PRICES_DOMAIN}"
[[ -z "$BACKEND_URL" ]] && BACKEND_URL="backend-${SLUG}.${PRICES_DOMAIN}"
if [[ "$EXPOSE_MONITORING" == true && -z "$MONITORING_URL" ]]; then
    MONITORING_URL="monitoring-${SLUG}.${PRICES_DOMAIN}"
fi

# ============================================================================
# Dry Run
# ============================================================================

if [[ "$DRY_RUN" == true ]]; then
    echo ""
    echo "=== DRY RUN ==="
    echo "Would deploy with:"
    echo "  Domain: ${PRICES_DOMAIN}"
    echo "  Artifact: ${ARTIFACT}"
    echo "  Slug: ${SLUG}"
    echo "  Frontend URL: ${FRONTEND_URL}"
    echo "  Backend URL: ${BACKEND_URL}"
    [[ -n "$CUSTOM_FRONTEND_URL" ]] && echo "  Custom Frontend: ${CUSTOM_FRONTEND_URL}"
    [[ -n "$CUSTOM_BACKEND_URL" ]] && echo "  Custom Backend: ${CUSTOM_BACKEND_URL}"
    [[ -n "$MONITORING_URL" ]] && echo "  Monitoring URL: ${MONITORING_URL}"
    [[ -n "$CUSTOM_MONITORING_URL" ]] && echo "  Custom Monitoring: ${CUSTOM_MONITORING_URL}"
    echo "  Expose Monitoring: ${EXPOSE_MONITORING}"
    echo "  Redeploy: ${REDEPLOY}"
    [[ -n "$INPUT_ENV_FILE" ]] && echo "  Env File: ${INPUT_ENV_FILE}"
    echo ""
    exit 0
fi

# ============================================================================
# Start Deployment
# ============================================================================

echo ""
echo "=============================================="
echo "  PRICES Fullstack Deployment"
echo "=============================================="
echo "  Slug: ${SLUG}"
echo "  Frontend: ${FRONTEND_URL}"
echo "  Backend: ${BACKEND_URL}"
echo "=============================================="
echo ""

START_TIME=$(date +%s)

# Temp file for env vars
ENV_FILE=$(mktemp)
trap "rm -f $ENV_FILE" EXIT

# ============================================================================
# Stage 1: Extract
# ============================================================================

log_stage "1. Extract Artifact"

EXTRACT_ARGS="--artifact $ARTIFACT --slug $SLUG"
[[ "$REDEPLOY" == true ]] && EXTRACT_ARGS="$EXTRACT_ARGS --force"

EXTRACT_OUTPUT=$("${LIB_DIR}/extract.sh" $EXTRACT_ARGS)
echo "$EXTRACT_OUTPUT"

# Parse output
EXTRACTED_PATH=$(echo "$EXTRACT_OUTPUT" | grep "^EXTRACTED_PATH=" | cut -d= -f2)

if [[ -z "$EXTRACTED_PATH" ]]; then
    log_error "Failed to get extracted path"
    exit 2
fi

log_info "Extracted to: ${EXTRACTED_PATH}"

# ============================================================================
# Stage 2: Environment Variables
# ============================================================================

log_stage "2. Environment Variables"

ENV_ARGS="--slug $SLUG --frontend-url $FRONTEND_URL --backend-url $BACKEND_URL --output $ENV_FILE"
[[ -n "$INPUT_ENV_FILE" ]] && ENV_ARGS="$ENV_ARGS --env-file $INPUT_ENV_FILE"

# Add extra env vars
for arg in "${EXTRA_ENV_VARS[@]}"; do
    ENV_ARGS="$ENV_ARGS $arg"
done

"${LIB_DIR}/env.sh" $ENV_ARGS

log_info "Environment variables generated"

# ============================================================================
# Stage 3: Prepare Distribution
# ============================================================================

log_stage "3. Prepare Distribution"

DIST_OUTPUT=$("${LIB_DIR}/prepare-dist.sh" --extracted-path "$EXTRACTED_PATH" --env-file "$ENV_FILE")
echo "$DIST_OUTPUT"

# Parse output
HAS_USER_COMPOSE=$(echo "$DIST_OUTPUT" | grep "^HAS_USER_COMPOSE=" | cut -d= -f2)
FRONTEND_DIST_PATH=$(echo "$DIST_OUTPUT" | grep "^FRONTEND_DIST_PATH=" | cut -d= -f2 || echo "")
BACKEND_DIST_PATH=$(echo "$DIST_OUTPUT" | grep "^BACKEND_DIST_PATH=" | cut -d= -f2 || echo "")

# ============================================================================
# Stage 4: Prepare Docker Compose
# ============================================================================

log_stage "4. Prepare Docker Compose"

COMPOSE_ARGS="--extracted-path $EXTRACTED_PATH --slug $SLUG --env-file $ENV_FILE"
[[ "$HAS_USER_COMPOSE" == "true" ]] && COMPOSE_ARGS="$COMPOSE_ARGS --has-user-compose"
[[ -n "$FRONTEND_DIST_PATH" ]] && COMPOSE_ARGS="$COMPOSE_ARGS --frontend-dist $FRONTEND_DIST_PATH"
[[ -n "$BACKEND_DIST_PATH" ]] && COMPOSE_ARGS="$COMPOSE_ARGS --backend-dist $BACKEND_DIST_PATH"

COMPOSE_OUTPUT=$("${LIB_DIR}/prepare-compose.sh" $COMPOSE_ARGS)
echo "$COMPOSE_OUTPUT"

# Parse output
COMPOSE_PATH=$(echo "$COMPOSE_OUTPUT" | grep "^COMPOSE_PATH=" | cut -d= -f2)
NETWORK_NAME=$(echo "$COMPOSE_OUTPUT" | grep "^NETWORK_NAME=" | cut -d= -f2)

# ============================================================================
# Stage 5: Docker Run
# ============================================================================

log_stage "5. Docker Run"

DOCKER_ARGS="--compose-path $COMPOSE_PATH --slug $SLUG"
[[ "$REDEPLOY" == true ]] && DOCKER_ARGS="$DOCKER_ARGS --redeploy"

"${LIB_DIR}/docker-run.sh" $DOCKER_ARGS

# ============================================================================
# Stage 6: Nginx Configure
# ============================================================================

log_stage "6. Nginx Configure"

NGINX_ARGS="--slug $SLUG --frontend-url $FRONTEND_URL --backend-url $BACKEND_URL"
[[ -n "$CUSTOM_FRONTEND_URL" ]] && NGINX_ARGS="$NGINX_ARGS --custom-frontend-url $CUSTOM_FRONTEND_URL"
[[ -n "$CUSTOM_BACKEND_URL" ]] && NGINX_ARGS="$NGINX_ARGS --custom-backend-url $CUSTOM_BACKEND_URL"
[[ -n "$MONITORING_URL" ]] && NGINX_ARGS="$NGINX_ARGS --monitoring-url $MONITORING_URL"
[[ -n "$CUSTOM_MONITORING_URL" ]] && NGINX_ARGS="$NGINX_ARGS --custom-monitoring-url $CUSTOM_MONITORING_URL"
[[ "$EXPOSE_MONITORING" == true ]] && NGINX_ARGS="$NGINX_ARGS --expose-monitoring"

"${LIB_DIR}/nginx.sh" $NGINX_ARGS

# ============================================================================
# Complete
# ============================================================================

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=============================================="
echo -e "  ${GREEN}Deployment Successful!${NC}"
echo "=============================================="
echo "  Duration: ${DURATION}s"
echo ""
echo "  URLs:"
echo "    Frontend: https://${FRONTEND_URL}"
[[ -n "$CUSTOM_FRONTEND_URL" ]] && echo "    Frontend (custom): https://${CUSTOM_FRONTEND_URL}"
echo "    Backend: https://${BACKEND_URL}"
[[ -n "$CUSTOM_BACKEND_URL" ]] && echo "    Backend (custom): https://${CUSTOM_BACKEND_URL}"
if [[ "$EXPOSE_MONITORING" == true && -n "$MONITORING_URL" ]]; then
    echo "    Monitoring: https://${MONITORING_URL}"
fi
echo "=============================================="
echo ""

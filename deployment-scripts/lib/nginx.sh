#!/bin/bash
# =============================================================================
# Nginx Stage - Port dari NginxStage.java
# =============================================================================
# Generates nginx config and reloads nginx
#
# Usage:
#   ./nginx.sh --slug <slug> --frontend-url <url> --backend-url <url> \
#              [--custom-frontend-url <url>] [--custom-backend-url <url>] \
#              [--monitoring-url <url>] [--custom-monitoring-url <url>] \
#              [--expose-monitoring]
#
# Arguments:
#   --slug                  Project slug (required)
#   --frontend-url          Default frontend URL (required)
#   --backend-url           Default backend URL (required)
#   --custom-frontend-url   Custom frontend URL (optional)
#   --custom-backend-url    Custom backend URL (optional)
#   --monitoring-url        Default monitoring URL (optional)
#   --custom-monitoring-url Custom monitoring URL (optional)
#   --expose-monitoring     Flag to expose monitoring (optional)
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
#   2 - Nginx config test failed
#   3 - Nginx reload failed
# =============================================================================

set -euo pipefail

# Constants (same as Java Constants.java)
NGINX_CONTAINER_NAME="${PRICES_NGINX_CONTAINER:-prices-nginx}"
NGINX_CONFIG_DIR="${PRICES_NGINX_CONFIG_DIR:-/var/prices/nginx/conf.d}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[NGINX]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[NGINX]${NC} $1"
}

log_error() {
    echo -e "${RED}[NGINX]${NC} $1" >&2
}

# ============================================================================
# Naming Utils
# ============================================================================

container_name() {
    local service=$1
    local slug=$2
    echo "prices-${slug}-${service}"
}

# ============================================================================
# Parse Arguments
# ============================================================================

SLUG=""
FRONTEND_URL=""
BACKEND_URL=""
CUSTOM_FRONTEND_URL=""
CUSTOM_BACKEND_URL=""
MONITORING_URL=""
CUSTOM_MONITORING_URL=""
EXPOSE_MONITORING=false

while [[ $# -gt 0 ]]; do
    case $1 in
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
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$SLUG" ]]; then
    log_error "Missing required argument: --slug"
    exit 1
fi

if [[ -z "$FRONTEND_URL" ]]; then
    log_error "Missing required argument: --frontend-url"
    exit 1
fi

if [[ -z "$BACKEND_URL" ]]; then
    log_error "Missing required argument: --backend-url"
    exit 1
fi

# ============================================================================
# Setup
# ============================================================================

FRONTEND_CONTAINER=$(container_name "frontend" "$SLUG")
BACKEND_CONTAINER=$(container_name "backend" "$SLUG")

# ============================================================================
# Generate Nginx Config
# ============================================================================

generate_server_block() {
    local server_names=$1
    local upstream_var=$2
    local upstream_url=$3
    local comment=$4
    
    cat << EOF
# ${comment}
server {
    listen 80;
    server_name ${server_names};

    resolver 127.0.0.11 valid=30s;
    set \$${upstream_var} ${upstream_url};

    location / {
        proxy_pass \$${upstream_var};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
EOF
}

# Build server names
FRONTEND_SERVER_NAMES="$FRONTEND_URL"
[[ -n "$CUSTOM_FRONTEND_URL" ]] && FRONTEND_SERVER_NAMES="$FRONTEND_SERVER_NAMES $CUSTOM_FRONTEND_URL"

BACKEND_SERVER_NAMES="$BACKEND_URL"
[[ -n "$CUSTOM_BACKEND_URL" ]] && BACKEND_SERVER_NAMES="$BACKEND_SERVER_NAMES $CUSTOM_BACKEND_URL"

MONITORING_SERVER_NAMES=""
[[ -n "$MONITORING_URL" ]] && MONITORING_SERVER_NAMES="$MONITORING_URL"
[[ -n "$CUSTOM_MONITORING_URL" ]] && MONITORING_SERVER_NAMES="$MONITORING_SERVER_NAMES $CUSTOM_MONITORING_URL"

# Generate config
CONFIG_CONTENT=""

# Frontend server block
CONFIG_CONTENT+=$(generate_server_block "$FRONTEND_SERVER_NAMES" "frontend_upstream" "http://${FRONTEND_CONTAINER}:80" "Frontend server")
CONFIG_CONTENT+="\n"

# Backend server block
CONFIG_CONTENT+=$(generate_server_block "$BACKEND_SERVER_NAMES" "backend_upstream" "http://${BACKEND_CONTAINER}:7776" "Backend server")
CONFIG_CONTENT+="\n"

# Monitoring server block (if exposed)
if [[ "$EXPOSE_MONITORING" == true && -n "$MONITORING_SERVER_NAMES" ]]; then
    CONFIG_CONTENT+=$(generate_server_block "$MONITORING_SERVER_NAMES" "monitoring_upstream" "http://${BACKEND_CONTAINER}:9464" "Monitoring server")
    CONFIG_CONTENT+="\n"
fi

# ============================================================================
# Write Config File
# ============================================================================

# Ensure config directory exists
mkdir -p "$NGINX_CONFIG_DIR"

CONFIG_FILE="${NGINX_CONFIG_DIR}/${SLUG}.conf"
echo -e "$CONFIG_CONTENT" > "$CONFIG_FILE"
log_info "Generated nginx config: ${CONFIG_FILE}"

# ============================================================================
# Test Nginx Config
# ============================================================================

log_info "Testing nginx config..."
if ! docker exec "$NGINX_CONTAINER_NAME" nginx -t 2>&1; then
    log_error "Nginx config test failed"
    rm -f "$CONFIG_FILE"
    exit 2
fi
log_info "Nginx config test passed"

# ============================================================================
# Reload Nginx
# ============================================================================

log_info "Reloading nginx..."
if ! docker exec "$NGINX_CONTAINER_NAME" nginx -s reload 2>&1; then
    log_error "Nginx reload failed"
    exit 3
fi
log_info "Nginx reloaded successfully"

# ============================================================================
# Log URLs
# ============================================================================

log_info "Active URLs:"
log_info "  Frontend: https://${FRONTEND_URL}"
[[ -n "$CUSTOM_FRONTEND_URL" ]] && log_info "  Frontend (custom): https://${CUSTOM_FRONTEND_URL}"
log_info "  Backend: https://${BACKEND_URL}"
[[ -n "$CUSTOM_BACKEND_URL" ]] && log_info "  Backend (custom): https://${CUSTOM_BACKEND_URL}"
if [[ "$EXPOSE_MONITORING" == true && -n "$MONITORING_URL" ]]; then
    log_info "  Monitoring: https://${MONITORING_URL}"
    [[ -n "$CUSTOM_MONITORING_URL" ]] && log_info "  Monitoring (custom): https://${CUSTOM_MONITORING_URL}"
fi

# ============================================================================
# Output
# ============================================================================

log_info "Nginx stage completed successfully"
echo ""
echo "CONFIG_FILE=${CONFIG_FILE}"

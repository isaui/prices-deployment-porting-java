#!/bin/bash
# =============================================================================
# Env Stage - Port dari EnvStage.java
# =============================================================================
# Merges and generates environment variables for deployment
#
# Usage:
#   ./env.sh --slug <project-slug> --frontend-url <url> --backend-url <url> \
#            [--env-file <path>] [--env KEY=VALUE]...
#
# Arguments:
#   --slug           Project slug/identifier (required)
#   --frontend-url   Frontend URL without https:// (required)
#   --backend-url    Backend URL without https:// (required)
#   --env-file       Path to existing .env file to merge (optional)
#   --env            Additional env var KEY=VALUE, can be repeated (optional)
#   --output         Output file path for final env vars (optional, default: stdout)
#
# Output:
#   Writes final env vars to stdout or --output file
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
    echo -e "${GREEN}[ENV]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[ENV]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ENV]${NC} $1" >&2
}

# ============================================================================
# Helper Functions (ported from NamingUtils.java)
# ============================================================================

generate_secure_password() {
    local length=${1:-16}
    openssl rand -hex "$length" | cut -c1-"$length"
}

full_url() {
    local domain=$1
    if [[ -z "$domain" ]]; then
        echo ""
    else
        echo "https://${domain}"
    fi
}

static_url() {
    local domain=$1
    if [[ -z "$domain" ]]; then
        echo ""
    else
        echo "https://${domain}/static"
    fi
}

database_url() {
    local slug=$1
    echo "jdbc:postgresql://postgres:5432/${slug}"
}

mask_value() {
    local key=$1
    local value=$2
    local upper_key="${key^^}"
    
    # Check if key contains sensitive words
    if [[ "$upper_key" == *"PASSWORD"* ]] || \
       [[ "$upper_key" == *"SECRET"* ]] || \
       [[ "$upper_key" == *"KEY"* ]] || \
       [[ "$upper_key" == *"TOKEN"* ]] || \
       [[ "$upper_key" == *"CREDENTIAL"* ]]; then
        local len=${#value}
        if [[ $len -gt 4 ]]; then
            echo "${value:0:2}****${value: -2}"
        else
            echo "****"
        fi
    else
        echo "$value"
    fi
}

# ============================================================================
# Parse Arguments
# ============================================================================

SLUG=""
FRONTEND_URL=""
BACKEND_URL=""
ENV_FILE=""
OUTPUT_FILE=""
declare -A INPUT_ENV_VARS

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
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --env)
            # Parse KEY=VALUE
            IFS='=' read -r key value <<< "$2"
            INPUT_ENV_VARS["$key"]="$value"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
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
# Build Environment Variables
# ============================================================================

declare -A FINAL_ENV_VARS

# 1. Generate default env vars (same as EnvStage.getDefaultEnvVars)
DB_URL=$(database_url "$SLUG")
DB_NAME="$SLUG"
DB_USER="postgres"
DB_PASSWORD=$(generate_secure_password 16)

VITE_BACKEND_URL=$(full_url "$BACKEND_URL")
VITE_SITE_URL=$(full_url "$FRONTEND_URL")
VITE_STATIC_SERVER_URL=$(static_url "$FRONTEND_URL")

# Apply defaults
FINAL_ENV_VARS["AMANAH_HOST_BE"]="0.0.0.0"
FINAL_ENV_VARS["AMANAH_PORT_BE"]="7776"
FINAL_ENV_VARS["AMANAH_DB_URL"]="$DB_URL"
FINAL_ENV_VARS["AMANAH_DB_USERNAME"]="$DB_USER"
FINAL_ENV_VARS["AMANAH_DB_NAME"]="$DB_NAME"
FINAL_ENV_VARS["AMANAH_DB_PASSWORD"]="$DB_PASSWORD"
FINAL_ENV_VARS["VITE_BACKEND_URL"]="$VITE_BACKEND_URL"
FINAL_ENV_VARS["VITE_SITE_URL"]="$VITE_SITE_URL"
FINAL_ENV_VARS["VITE_STATIC_SERVER_URL"]="$VITE_STATIC_SERVER_URL"
FINAL_ENV_VARS["VITE_PORT"]="3000"

log_info "Applied 10 default env vars"

# 2. Merge existing env vars from file (overrides defaults)
if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
    existing_count=0
    while IFS='=' read -r key value || [[ -n "$key" ]]; do
        # Skip empty lines and comments
        [[ -z "$key" || "$key" == \#* ]] && continue
        # Remove quotes if present
        value="${value%\"}"
        value="${value#\"}"
        value="${value%\'}"
        value="${value#\'}"
        FINAL_ENV_VARS["$key"]="$value"
        ((existing_count++)) || true
    done < "$ENV_FILE"
    log_info "Merged $existing_count existing env vars from $ENV_FILE"
fi

# 3. Merge input env vars (overrides both)
input_count=${#INPUT_ENV_VARS[@]}
if [[ $input_count -gt 0 ]]; then
    for key in "${!INPUT_ENV_VARS[@]}"; do
        FINAL_ENV_VARS["$key"]="${INPUT_ENV_VARS[$key]}"
    done
    log_info "Merged $input_count input env vars"
fi

# ============================================================================
# Output
# ============================================================================

log_info "Final env vars: ${#FINAL_ENV_VARS[@]}"

# Sort keys and output
output_content=""
for key in $(echo "${!FINAL_ENV_VARS[@]}" | tr ' ' '\n' | sort); do
    value="${FINAL_ENV_VARS[$key]}"
    masked=$(mask_value "$key" "$value")
    log_info "  $key=$masked"
    output_content+="${key}=${value}\n"
done

# Write to file or stdout
if [[ -n "$OUTPUT_FILE" ]]; then
    echo -e "$output_content" > "$OUTPUT_FILE"
    log_info "Wrote env vars to: $OUTPUT_FILE"
else
    echo -e "$output_content"
fi

log_info "Env stage completed successfully"

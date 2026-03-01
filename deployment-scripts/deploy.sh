#!/bin/bash
# =============================================================================
# PRICES Deploy Script
# =============================================================================
# Simplified deployment script that calls the PRICES Agent API
#
# Usage:
#   ./deploy.sh --project-name <name> --artifact <path> [options]
#
# Required:
#   --project-name          Project name/slug
#   --artifact              Path to artifact zip file
#
# Optional:
#   --frontend-url          Custom frontend URL
#   --backend-url           Custom backend URL
#   --frontend-port         Frontend internal port (default: 80)
#   --backend-port          Backend internal port (default: 7776)
#   --env KEY=VALUE         Environment variable (can be repeated)
#
# Environment (from .env file or environment):
#   AGENT_URL               PRICES Agent API URL
#   INTERNAL_API_KEY        Internal API key for authentication
# =============================================================================

set -euo pipefail

# Debug: immediate output to confirm script started
echo "[deploy.sh] Script started"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "[deploy.sh] SCRIPT_DIR=$SCRIPT_DIR"

# Simple log functions - no colors for better compatibility
log_info() { echo "[INFO] $1"; }
log_warn() { echo "[WARN] $1"; }
log_error() { echo "[ERROR] $1" >&2; }
log_step() { echo "[STEP] $1"; }

# =============================================================================
# Load Environment
# =============================================================================

load_env() {
    local env_file="${SCRIPT_DIR}/.env"
    
    if [[ -f "$env_file" ]]; then
        log_info "Loading environment from ${env_file}"
        set -a
        source "$env_file"
        set +a
    fi
    
    # Validate required env vars
    if [[ -z "${AGENT_URL:-}" ]]; then
        log_error "AGENT_URL is not set. Set it in .env or environment."
        exit 1
    fi
    
    if [[ -z "${INTERNAL_API_KEY:-}" ]]; then
        log_error "INTERNAL_API_KEY is not set. Set it in .env or environment."
        exit 1
    fi
}

# =============================================================================
# Parse Arguments
# =============================================================================

PROJECT_NAME=""
ARTIFACT=""
FRONTEND_URL=""
BACKEND_URL=""
FRONTEND_PORT="80"
BACKEND_PORT="7776"
declare -a ENV_VARS=()

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --project-name)
                PROJECT_NAME="$2"
                shift 2
                ;;
            --artifact)
                ARTIFACT="$2"
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
            --frontend-port)
                FRONTEND_PORT="$2"
                shift 2
                ;;
            --backend-port)
                BACKEND_PORT="$2"
                shift 2
                ;;
            --env)
                ENV_VARS+=("$2")
                shift 2
                ;;
            --help|-h)
                head -25 "$0" | tail -20
                exit 0
                ;;
            *)
                log_error "Unknown argument: $1"
                exit 1
                ;;
        esac
    done
    
    # Validate required args
    if [[ -z "$PROJECT_NAME" ]]; then
        log_error "Missing required argument: --project-name"
        exit 1
    fi
    
    if [[ -z "$ARTIFACT" ]]; then
        log_error "Missing required argument: --artifact"
        exit 1
    fi
    
    if [[ ! -f "$ARTIFACT" ]]; then
        log_error "Artifact file not found: $ARTIFACT"
        exit 1
    fi
}

# =============================================================================
# API Functions
# =============================================================================

api_call() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"
    
    local url="${AGENT_URL}${endpoint}"
    # Add timeout: 30s connect, 300s max (5 min for deploy)
    local args=(-s -X "$method" --connect-timeout 30 --max-time 300 -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" -H "Content-Type: application/json")
    
    if [[ -n "$data" ]]; then
        args+=(-d "$data")
    fi
    
    curl "${args[@]}" "$url"
}

# Upload artifact using chunked upload
upload_artifact() {
    local file="$1"
    local project_slug="$2"
    local file_name=$(basename "$file")
    local file_size=$(stat -c%s "$file")
    local chunk_size=$((5 * 1024 * 1024))  # 5MB chunks
    local total_chunks=$(( (file_size + chunk_size - 1) / chunk_size ))
    
    log_step "Uploading artifact: ${file_name} (${file_size} bytes, ${total_chunks} chunks)"
    
    # Init upload
    local init_request="{\"projectSlug\":\"${project_slug}\",\"fileName\":\"${file_name}\",\"totalSize\":${file_size},\"totalChunks\":${total_chunks}}"
    echo "[DEBUG] POST /api/internal/uploads/init"
    echo "[DEBUG] Request: $init_request"
    
    local init_response=$(api_call POST "/api/internal/uploads/init" "$init_request")
    echo "[DEBUG] Response: $init_response"
    
    if echo "$init_response" | grep -q '"success":false'; then
        log_error "Failed to init upload: $init_response"
        exit 2
    fi
    
    # Upload chunks
    local offset=0
    local chunk_index=0
    
    while [[ $offset -lt $file_size ]]; do
        local chunk_file=$(mktemp)
        dd if="$file" bs=1 skip=$offset count=$chunk_size of="$chunk_file" 2>/dev/null
        
        local chunk_response=$(curl -s -X POST \
            -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" \
            -F "chunk=@${chunk_file}" \
            "${AGENT_URL}/api/internal/uploads/${project_slug}/chunk?index=${chunk_index}")
        
        rm -f "$chunk_file"
        
        if echo "$chunk_response" | grep -q '"success":false'; then
            log_error "Failed to upload chunk ${chunk_index}: $chunk_response"
            exit 2
        fi
        
        echo -ne "\r  Uploaded chunk $((chunk_index + 1))/${total_chunks}"
        
        offset=$((offset + chunk_size))
        chunk_index=$((chunk_index + 1))
    done
    
    echo ""
    
    # Finalize upload
    echo "[DEBUG] POST /api/internal/uploads/${project_slug}/finalize"
    local finalize_response=$(api_call POST "/api/internal/uploads/${project_slug}/finalize" "")
    echo "[DEBUG] Response: $finalize_response"
    
    if echo "$finalize_response" | grep -q '"success":false'; then
        log_error "Failed to finalize upload: $finalize_response"
        exit 2
    fi
    
    log_info "Upload complete"
}

# Create project
create_project() {
    log_step "Creating project..."
    
    # Build request (matches CreateInternalProjectRequest DTO)
    local request="{\"name\":\"${PROJECT_NAME}\""
    [[ -n "$FRONTEND_URL" ]] && request+=",\"customFrontendUrl\":\"${FRONTEND_URL}\""
    [[ -n "$BACKEND_URL" ]] && request+=",\"customBackendUrl\":\"${BACKEND_URL}\""
    request+=",\"frontendListeningPort\":${FRONTEND_PORT}"
    request+=",\"backendListeningPort\":${BACKEND_PORT}"
    request+="}"
    
    echo "[DEBUG] POST /api/internal/projects"
    echo "[DEBUG] Request: $request"
    
    local response=$(api_call POST "/api/internal/projects" "$request")
    
    echo "[DEBUG] Response: $response"
    
    if echo "$response" | grep -q '"success":false'; then
        log_error "Failed to create project: $response"
        exit 3
    fi
    
    # Extract project ID and slug
    local project_id=$(echo "$response" | grep -o '"projectId":[0-9]*' | cut -d: -f2)
    local project_slug=$(echo "$response" | grep -o '"slug":"[^"]*"' | cut -d'"' -f4)
    
    echo "[DEBUG] Extracted: project_id=$project_id, slug=$project_slug"
    
    if [[ -z "$project_id" ]]; then
        log_error "Failed to get project ID from response: $response"
        exit 3
    fi
    
    log_info "Project created: ID=${project_id}, slug=${project_slug}"
    echo "$project_id:$project_slug"
}

# Deploy project
deploy_project() {
    local project_id="$1"
    
    log_step "Starting deployment..."
    
    # Build env vars JSON object
    local env_json="{}"
    if [[ ${#ENV_VARS[@]} -gt 0 ]]; then
        env_json="{"
        for i in "${!ENV_VARS[@]}"; do
            local kv="${ENV_VARS[$i]}"
            local key="${kv%%=*}"
            local value="${kv#*=}"
            [[ $i -gt 0 ]] && env_json+=","
            env_json+="\"${key}\":\"${value}\""
        done
        env_json+="}"
    fi
    
    # Build request
    local request="{\"version\":\"1.0.0\",\"envVars\":${env_json}}"
    
    echo "[DEBUG] POST /api/internal/projects/${project_id}/deploy"
    echo "[DEBUG] Request: $request"
    
    local response=$(api_call POST "/api/internal/projects/${project_id}/deploy" "$request")
    
    echo "[DEBUG] Response: $response"
    
    if echo "$response" | grep -q '"success":false'; then
        log_error "Deployment failed: $response"
        exit 4
    fi
    
    # Extract deployment ID
    local deployment_id=$(echo "$response" | grep -o '"deploymentId":[0-9]*' | cut -d: -f2)
    
    echo "[DEBUG] Extracted: deployment_id=$deployment_id"
    
    if [[ -z "$deployment_id" ]]; then
        log_error "Failed to get deployment ID from response: $response"
        exit 4
    fi
    
    log_info "Deployment started with ID: ${deployment_id}"
    echo "$deployment_id"
}

# Stream deployment logs
stream_logs() {
    local deployment_id="$1"
    
    log_step "Streaming deployment logs..."
    echo ""
    
    curl -s -N \
        -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" \
        "${AGENT_URL}/api/internal/deployments/${deployment_id}/stream" | while read -r line; do
        # Parse SSE data
        if [[ "$line" == data:* ]]; then
            echo "${line#data:}"
        fi
    done
    
    echo ""
}

# =============================================================================
# Main
# =============================================================================

main() {
    load_env
    parse_args "$@"
    
    echo ""
    echo "=========================================="
    echo "  PRICES Deployment"
    echo "=========================================="
    echo "  Project: ${PROJECT_NAME}"
    echo "  Artifact: ${ARTIFACT}"
    [[ -n "$FRONTEND_URL" ]] && echo "  Frontend URL: ${FRONTEND_URL}"
    [[ -n "$BACKEND_URL" ]] && echo "  Backend URL: ${BACKEND_URL}"
    echo "=========================================="
    echo ""
    
    # Create project
    project_result=$(create_project)
    echo "[DEBUG] create_project returned: $project_result"
    
    # Get last line (the actual result, not debug output)
    project_result=$(echo "$project_result" | tail -1)
    project_id=$(echo "$project_result" | cut -d: -f1)
    project_slug=$(echo "$project_result" | cut -d: -f2)
    
    echo "[DEBUG] Parsed: project_id=$project_id, project_slug=$project_slug"
    
    # Upload artifact (use slug for upload)
    upload_artifact "$ARTIFACT" "$project_slug"
    
    # Deploy
    deployment_id=$(deploy_project "$project_id")
    echo "[DEBUG] deploy_project returned: $deployment_id"
    
    # Get last line (the actual result)
    deployment_id=$(echo "$deployment_id" | tail -1)
    echo "[DEBUG] Parsed deployment_id: $deployment_id"
    
    # Stream logs
    stream_logs "$deployment_id"
    
    log_info "Deployment complete!"
}

main "$@"

#!/bin/bash
# =============================================================================
# Prepare Distribution Stage - Port dari PrepareDistStage.java
# =============================================================================
# Prepares frontend and backend distribution directories with Dockerfiles
#
# Usage:
#   ./prepare-dist.sh --extracted-path <path> [--env-file <path>]
#
# Arguments:
#   --extracted-path   Path to extracted artifact directory (required)
#   --env-file         Path to env file for frontend build args (optional)
#
# Output:
#   FRONTEND_DIST_PATH - Path to frontend distribution (if exists)
#   BACKEND_DIST_PATH  - Path to backend distribution (if exists)
#   HAS_USER_COMPOSE   - "true" if user provided docker-compose.yml
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
#   2 - No frontend or backend found
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[PREPARE-DIST]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[PREPARE-DIST]${NC} $1"
}

log_error() {
    echo -e "${RED}[PREPARE-DIST]${NC} $1" >&2
}

# ============================================================================
# Parse Arguments
# ============================================================================

EXTRACTED_PATH=""
ENV_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --extracted-path)
            EXTRACTED_PATH="$2"
            shift 2
            ;;
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$EXTRACTED_PATH" ]]; then
    log_error "Missing required argument: --extracted-path"
    exit 1
fi

# ============================================================================
# Check for user-provided docker-compose.yml
# ============================================================================

COMPOSE_PATH="${EXTRACTED_PATH}/docker-compose.yml"
if [[ -f "$COMPOSE_PATH" ]]; then
    log_info "User docker-compose.yml found, skipping dist preparation"
    echo "HAS_USER_COMPOSE=true"
    exit 0
fi

# ============================================================================
# Helper Functions
# ============================================================================

# Find frontend root with priority: Dockerfile > package.json > build/dist
find_frontend_root() {
    local base_path=$1
    
    if [[ -f "${base_path}/Dockerfile" ]]; then
        echo "${base_path}|dockerfile"
        return
    fi
    
    if [[ -f "${base_path}/package.json" ]]; then
        echo "${base_path}|package.json"
        return
    fi
    
    if [[ -d "${base_path}/build" ]]; then
        echo "${base_path}/build|static"
        return
    fi
    
    if [[ -d "${base_path}/dist" ]]; then
        echo "${base_path}/dist|static"
        return
    fi
    
    echo ""
}

# Find backend root: Dockerfile or product-module
find_backend_root() {
    local base_path=$1
    
    if [[ -f "${base_path}/Dockerfile" ]]; then
        echo "${base_path}|dockerfile"
    else
        echo "${base_path}|product-module"
    fi
}

# Filter VITE_* and REACT_APP_* env vars from file
filter_frontend_env_vars() {
    local env_file=$1
    
    if [[ -z "$env_file" || ! -f "$env_file" ]]; then
        return
    fi
    
    grep -E "^(VITE_|REACT_APP_)" "$env_file" 2>/dev/null || true
}

# Generate frontend Dockerfile
generate_frontend_dockerfile() {
    local marker=$1
    local env_file=$2
    
    if [[ "$marker" == "package.json" ]]; then
        cat << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
EOF
        
        # Add ARG and ENV for frontend env vars
        if [[ -n "$env_file" && -f "$env_file" ]]; then
            while IFS='=' read -r key value || [[ -n "$key" ]]; do
                [[ -z "$key" || "$key" == \#* ]] && continue
                if [[ "$key" == VITE_* || "$key" == REACT_APP_* ]]; then
                    echo "ARG ${key}"
                fi
            done < "$env_file"
            
            while IFS='=' read -r key value || [[ -n "$key" ]]; do
                [[ -z "$key" || "$key" == \#* ]] && continue
                if [[ "$key" == VITE_* || "$key" == REACT_APP_* ]]; then
                    echo "ENV ${key}=\$${key}"
                fi
            done < "$env_file"
        fi
        
        cat << 'EOF'
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Move output to consistent location
RUN if [ -d "dist" ]; then mv dist output; elif [ -d "build" ]; then mv build output; fi

FROM nginx:alpine
COPY --from=builder /app/output /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
    else
        # Static mode
        cat << 'EOF'
FROM nginx:alpine
COPY . /usr/share/nginx/html
EXPOSE 80
CMD ["sh", "-c", "if [ -z \"$(ls -A /usr/share/nginx/html)\" ]; then echo 'ERROR: No static files found!'; exit 1; fi; nginx -g 'daemon off;'"]
EOF
    fi
}

# Generate backend Dockerfile
generate_backend_dockerfile() {
    cat << 'EOF'
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY . .
EXPOSE 7776
CMD ["sh", "-c", "MODULE_DIR=$(find . -type d -name '*.product.*' | head -1); if [ -z \"$MODULE_DIR\" ]; then echo 'ERROR: No product module found!'; exit 1; fi; java -cp $MODULE_DIR --module-path $MODULE_DIR -m $(basename $MODULE_DIR)"]
EOF
}

# ============================================================================
# Prepare Frontend
# ============================================================================

FRONTEND_PATH="${EXTRACTED_PATH}/frontend"
FRONTEND_DIST_PATH=""
FRONTEND_BUILD_ARGS=""

if [[ -d "$FRONTEND_PATH" ]]; then
    log_info "Preparing frontend distribution..."
    
    # Find frontend root
    root_info=$(find_frontend_root "$FRONTEND_PATH")
    
    if [[ -z "$root_info" ]]; then
        log_error "Could not find frontend root"
        exit 2
    fi
    
    IFS='|' read -r root_path marker <<< "$root_info"
    log_info "Frontend root found via ${marker}: ${root_path}"
    
    FRONTEND_DIST_PATH="${EXTRACTED_PATH}/frontend-dist"
    
    # Copy directory
    cp -r "$root_path" "$FRONTEND_DIST_PATH"
    
    # Clean build output folders for package.json mode
    if [[ "$marker" == "package.json" ]]; then
        for folder in dist build node_modules; do
            folder_path="${FRONTEND_DIST_PATH}/${folder}"
            if [[ -d "$folder_path" ]]; then
                log_info "Removing ${folder}/ (will be rebuilt)"
                rm -rf "$folder_path"
            fi
        done
    fi
    
    # Generate Dockerfile if not present
    dockerfile_path="${FRONTEND_DIST_PATH}/Dockerfile"
    if [[ ! -f "$dockerfile_path" ]]; then
        log_info "Generating Dockerfile for frontend (${marker} mode)"
        generate_frontend_dockerfile "$marker" "$ENV_FILE" > "$dockerfile_path"
        
        # Count frontend env vars
        if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
            env_count=$(grep -cE "^(VITE_|REACT_APP_)" "$ENV_FILE" 2>/dev/null || echo "0")
            if [[ "$env_count" -gt 0 ]]; then
                log_info "Dockerfile includes ${env_count} build args (VITE_*/REACT_*)"
            fi
        fi
    else
        log_info "Using existing Dockerfile"
    fi
    
    log_info "Frontend dist ready: ${FRONTEND_DIST_PATH}"
else
    log_info "No frontend directory found, skipping"
fi

# ============================================================================
# Prepare Backend
# ============================================================================

BACKEND_PATH="${EXTRACTED_PATH}/backend"
BACKEND_DIST_PATH=""

if [[ -d "$BACKEND_PATH" ]]; then
    log_info "Preparing backend distribution..."
    
    # Find backend root
    root_info=$(find_backend_root "$BACKEND_PATH")
    IFS='|' read -r root_path marker <<< "$root_info"
    log_info "Backend root found via ${marker}: ${root_path}"
    
    BACKEND_DIST_PATH="${EXTRACTED_PATH}/backend-dist"
    
    # Copy directory
    cp -r "$root_path" "$BACKEND_DIST_PATH"
    
    # Generate Dockerfile if not present
    dockerfile_path="${BACKEND_DIST_PATH}/Dockerfile"
    if [[ ! -f "$dockerfile_path" ]]; then
        log_info "Generating Dockerfile for backend (${marker} mode)"
        generate_backend_dockerfile > "$dockerfile_path"
    else
        log_info "Using existing Dockerfile"
    fi
    
    log_info "Backend dist ready: ${BACKEND_DIST_PATH}"
else
    log_info "No backend directory found, skipping"
fi

# ============================================================================
# Validate
# ============================================================================

if [[ -z "$FRONTEND_DIST_PATH" && -z "$BACKEND_DIST_PATH" ]]; then
    log_error "No frontend or backend found in artifact"
    exit 2
fi

# ============================================================================
# Output
# ============================================================================

log_info "Prepare distribution stage completed successfully"
echo ""
echo "HAS_USER_COMPOSE=false"
[[ -n "$FRONTEND_DIST_PATH" ]] && echo "FRONTEND_DIST_PATH=${FRONTEND_DIST_PATH}"
[[ -n "$BACKEND_DIST_PATH" ]] && echo "BACKEND_DIST_PATH=${BACKEND_DIST_PATH}"

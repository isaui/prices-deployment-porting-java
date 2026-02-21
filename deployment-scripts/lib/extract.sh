#!/bin/bash
# =============================================================================
# Extract Stage - Port dari ExtractStage.java
# =============================================================================
# Extracts artifact zip to deployment directory
#
# Usage:
#   ./extract.sh --artifact <path> --slug <project-slug> [--force]
#
# Arguments:
#   --artifact   Path to artifact zip file (required)
#   --slug       Project slug/identifier (required)
#   --force      Remove existing directory if exists (optional)
#
# Output:
#   EXTRACTED_PATH - Path where files were extracted
#
# Exit codes:
#   0 - Success
#   1 - Missing required arguments
#   2 - Artifact file not found
#   3 - Invalid project structure
#   4 - Extraction failed
# =============================================================================

set -euo pipefail

# Constants (same as Java Constants.java)
DEPLOYMENTS_BASE_DIR="${PRICES_DEPLOYMENTS_DIR:-/var/prices/deployments}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[EXTRACT]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[EXTRACT]${NC} $1"
}

log_error() {
    echo -e "${RED}[EXTRACT]${NC} $1" >&2
}

# Parse named arguments
ARTIFACT=""
SLUG=""
FORCE=false

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
        --force)
            FORCE=true
            shift
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$ARTIFACT" ]]; then
    log_error "Missing required argument: --artifact"
    echo "Usage: $0 --artifact <path> --slug <project-slug> [--force]"
    exit 1
fi

if [[ -z "$SLUG" ]]; then
    log_error "Missing required argument: --slug"
    echo "Usage: $0 --artifact <path> --slug <project-slug> [--force]"
    exit 1
fi

# Validate artifact file exists
if [[ ! -f "$ARTIFACT" ]]; then
    log_error "Artifact file not found: $ARTIFACT"
    exit 2
fi

# 1. Create deployment directory
DEPLOY_DIR="${DEPLOYMENTS_BASE_DIR}/${SLUG}"

# Remove existing directory if exists (redeploy case)
if [[ -d "$DEPLOY_DIR" ]]; then
    if [[ "$FORCE" == true ]]; then
        log_warn "Removing existing deployment directory: $DEPLOY_DIR"
        rm -rf "$DEPLOY_DIR"
    else
        log_error "Deployment directory already exists: $DEPLOY_DIR"
        log_error "Use --force to overwrite"
        exit 1
    fi
fi

mkdir -p "$DEPLOY_DIR"
log_info "Created deployment directory: $DEPLOY_DIR"

# 2. Extract zip
log_info "Extracting artifact: $ARTIFACT"

# Use unzip with security checks (similar to Zip Slip protection)
# -d: extract to directory
# -o: overwrite without prompting
if ! unzip -o -d "$DEPLOY_DIR" "$ARTIFACT"; then
    log_error "Failed to extract artifact"
    rm -rf "$DEPLOY_DIR"
    exit 4
fi

# Count extracted files
FILE_COUNT=$(find "$DEPLOY_DIR" -type f | wc -l)
log_info "Extracted $FILE_COUNT files to: $DEPLOY_DIR"

# 3. Validate required folders exist
FRONTEND_PATH="${DEPLOY_DIR}/frontend"
BACKEND_PATH="${DEPLOY_DIR}/backend"

FRONTEND_EXISTS=false
BACKEND_EXISTS=false

if [[ -d "$FRONTEND_PATH" ]]; then
    FRONTEND_EXISTS=true
    log_info "Found frontend/ folder"
fi

if [[ -d "$BACKEND_PATH" ]]; then
    BACKEND_EXISTS=true
    log_info "Found backend/ folder"
fi

if [[ "$FRONTEND_EXISTS" == false && "$BACKEND_EXISTS" == false ]]; then
    log_error "Invalid project structure: neither frontend/ nor backend/ folder found"
    rm -rf "$DEPLOY_DIR"
    exit 3
fi

# Export result for pipeline
export EXTRACTED_PATH="$DEPLOY_DIR"

log_info "Extract stage completed successfully"
echo ""
echo "EXTRACTED_PATH=$DEPLOY_DIR"

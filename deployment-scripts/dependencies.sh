#!/bin/bash
# Install dependencies untuk deployment scripts
set -euo pipefail

if [ -f /etc/os-release ]; then
    . /etc/os-release
    case $ID in
        ubuntu|debian) apt-get update && apt-get install -y unzip openssl ;;
        alpine) apk add --no-cache unzip openssl ;;
        *) echo "Install manually: unzip, openssl" ;;
    esac
fi

# chmod scripts
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$SCRIPT_DIR"/*.sh "$SCRIPT_DIR"/lib/*.sh 2>/dev/null || true
echo "Done"

#!/usr/bin/env bash
#
# cleanup.sh - Delete projects created by load-test.sh.
#
# Prerequisites:
#   - Java 17+ installed
#   - prices-cli.jar in the same directory as this script
#   - Already logged in
#   - jq installed
#
# Usage:
#   ./cleanup.sh <TIMESTAMP>     # Delete projects with prefix loadtest-<TIMESTAMP>-*
#   ./cleanup.sh --all           # Delete ALL projects with prefix loadtest-*
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="java -jar ${SCRIPT_DIR}/prices-cli.jar"

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <TIMESTAMP> | --all"
    exit 1
fi

ARG=$1

if [[ "$ARG" == "--all" ]]; then
    FILTER="loadtest-"
    echo "Cleaning up ALL loadtest-* projects..."
else
    FILTER="loadtest-${ARG}-"
    echo "Cleaning up projects with prefix: $FILTER"
fi

projects_json=$($CLI projects --json 2>&1)
slugs=$(echo "$projects_json" | jq -r --arg prefix "$FILTER" '.data[]?.slug | select(startswith($prefix))' | tr -d '\r\n' | tr '\n' ' ')

if [[ -z "$slugs" ]]; then
    echo "No matching projects found."
    exit 0
fi

count=0
fail=0
for slug in $slugs; do
    # Trim whitespace
    slug=$(echo "$slug" | xargs)
    echo "  Attempting to delete: $slug"
    if output=$($CLI delete "$slug" -y 2>&1); then
        echo "  ✓ Deleted: $slug"
        ((count++))
    else
        echo "  ✗ FAILED to delete: $slug"
        echo "    Error: $output"
        ((fail++))
    fi
    # Add small delay to avoid overwhelming the server
    sleep 1
done

echo
echo "Deleted: $count"
echo "Failed : $fail"

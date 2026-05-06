#!/usr/bin/env bash
#
# load-test.sh - Load testing deployment pipeline via prices CLI.
#
# Prerequisites:
#   - Java 17+ installed
#   - prices-cli.jar in the same directory as this script
#   - Already logged in: java -jar prices-cli.jar login -u <user> -p <pass>
#   - jq installed
#
# Usage:
#   ./load-test.sh <N> <frontend-path> <backend-path>
#
# Example:
#   ./load-test.sh 5 ./dummy-artifact/frontend ./dummy-artifact/backend
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="java -jar ${SCRIPT_DIR}/prices-cli.jar"

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 <N> <frontend-path> <backend-path>"
    exit 1
fi

N=$1
FRONTEND_PATH=$2
BACKEND_PATH=$3
PRODUCT_LINE="test"
TIMESTAMP=$(date +%s)
PREFIX="loadtest-${TIMESTAMP}"
LOG_DIR="logs/${PREFIX}"
RESULTS_CSV="${LOG_DIR}/results.csv"

if [[ ! -d "$FRONTEND_PATH" ]]; then
    echo "Frontend path not found: $FRONTEND_PATH"
    exit 1
fi
if [[ ! -d "$BACKEND_PATH" ]]; then
    echo "Backend path not found: $BACKEND_PATH"
    exit 1
fi

mkdir -p "$LOG_DIR"
echo "index,slug,status,upload_sec,deploy_sec,total_sec" > "$RESULTS_CSV"

echo "=========================================="
echo "Load Test Configuration"
echo "=========================================="
echo "Concurrent deployments : $N"
echo "Frontend path          : $FRONTEND_PATH"
echo "Backend path           : $BACKEND_PATH"
echo "Product line           : $PRODUCT_LINE"
echo "Prefix                 : $PREFIX"
echo "Log directory          : $LOG_DIR"
echo "=========================================="
echo

# Step 1: Create N projects
echo "[1/3] Creating $N projects..."
declare -a SLUGS
for ((i=1; i<=N; i++)); do
    name="${PREFIX}-${i}"
    response=$($CLI create -n "$name" -p "$PRODUCT_LINE" --json 2>&1)
    slug=$(echo "$response" | jq -r '.data.slug // empty')
    if [[ -z "$slug" ]]; then
        echo "  [$i] FAILED to create project: $response"
        SLUGS[i]=""
    else
        echo "  [$i] Created: $slug"
        SLUGS[i]=$slug
    fi
done
echo

# Step 2: Trigger N deployments in parallel
echo "[2/3] Triggering $N deployments in parallel..."
START_TIME=$(date +%s)
declare -a PIDS
declare -a START_TIMES

for ((i=1; i<=N; i++)); do
    slug="${SLUGS[i]}"
    if [[ -z "$slug" ]]; then
        continue
    fi
    START_TIMES[i]=$(date +%s)
    (
        $CLI deploy --frontend "$FRONTEND_PATH" --backend "$BACKEND_PATH" -p "$slug" > "${LOG_DIR}/deploy-${slug}.log" 2>&1
        echo $? > "${LOG_DIR}/exit-${slug}.code"
    ) &
    PIDS[i]=$!
    echo "  [$i] Started deploy for $slug (PID: ${PIDS[i]})"
done
echo

# Step 3: Wait for all and collect results
echo "[3/3] Waiting for all deployments to complete..."
SUCCESS_COUNT=0
FAIL_COUNT=0

for ((i=1; i<=N; i++)); do
    slug="${SLUGS[i]}"
    if [[ -z "$slug" ]]; then
        echo "$i,,create_failed,0,0,0" >> "$RESULTS_CSV"
        ((FAIL_COUNT++))
        continue
    fi
    wait "${PIDS[i]}" 2>/dev/null
    end_time=$(date +%s)
    total_duration=$((end_time - START_TIMES[i]))

    exit_code=$(cat "${LOG_DIR}/exit-${slug}.code" 2>/dev/null || echo "1")
    if [[ "$exit_code" == "0" ]]; then
        status="success"
        ((SUCCESS_COUNT++))
    else
        status="failed"
        ((FAIL_COUNT++))
    fi
    
    # Get deployment history from API to extract accurate timing
    upload_time=0
    deploy_time=0
    
    # Fetch latest deployment for this project
    deployment_json=$($CLI deployments "$slug" --json 2>&1 | jq '.[0]' 2>/dev/null)
    
    if [[ -n "$deployment_json" && "$deployment_json" != "null" ]]; then
        started_at=$(echo "$deployment_json" | jq -r '.startedAt // empty')
        finished_at=$(echo "$deployment_json" | jq -r '.finishedAt // empty')
        
        if [[ -n "$started_at" && -n "$finished_at" ]]; then
            # Convert ISO timestamps to epoch seconds
            started_epoch=$(date -d "$started_at" +%s 2>/dev/null || echo "0")
            finished_epoch=$(date -d "$finished_at" +%s 2>/dev/null || echo "0")
            
            if [[ "$started_epoch" -gt 0 && "$finished_epoch" -gt 0 ]]; then
                deploy_time=$((finished_epoch - started_epoch))
                upload_time=$((total_duration - deploy_time))
            fi
        fi
    fi
    
    # Fallback if API call failed
    if [[ "$deploy_time" -eq 0 ]]; then
        upload_time=15  # Rough estimate based on 32MB upload
        deploy_time=$((total_duration - upload_time))
    fi
    
    echo "  [$i] $slug: $status (total: ${total_duration}s, upload: ${upload_time}s, deploy: ${deploy_time}s)"
    echo "$i,$slug,$status,$upload_time,$deploy_time,$total_duration" >> "$RESULTS_CSV"
done

END_TIME=$(date +%s)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Total wall-clock time  : ${TOTAL_DURATION}s"
echo "Successful             : $SUCCESS_COUNT"
echo "Failed                 : $FAIL_COUNT"
echo "Results CSV            : $RESULTS_CSV"
echo "=========================================="
echo
echo "To clean up the projects created by this test, run:"
echo "  ./cleanup.sh $TIMESTAMP"

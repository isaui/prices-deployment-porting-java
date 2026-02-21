#!/bin/bash

# =============================================
# Setup Kawung - SSH Jump Host Simulation
# =============================================

# Jalankan docker-compose-kawung dulu
# pass ssh kawung 'root:kawung123'
# Set env variable sebelum jalankan script ini:
#   export AMANAH_IP=<ip-vm-amanah>
#   export AMANAH_SSH_PORT=22       (opsional, default 22)
#   export LOCAL_TUNNEL_PORT=9999   (opsional, default 9999)

AMANAH_IP="${AMANAH_IP:?Error: AMANAH_IP belum di-set}"
AMANAH_SSH_PORT="${AMANAH_SSH_PORT:-22}"
LOCAL_TUNNEL_PORT="${LOCAL_TUNNEL_PORT:-9999}"

echo "=== Membuka SSH Tunnel via Kawung → Amanah ==="
echo "Kawung yang konek ke Amanah ($AMANAH_IP:$AMANAH_SSH_PORT), bukan laptop."
echo

# -L: forward localhost:LOCAL_TUNNEL_PORT → AMANAH_IP:AMANAH_SSH_PORT via Kawung
# -N: tunnel doang
ssh -o StrictHostKeyChecking=no \
    -L ${LOCAL_TUNNEL_PORT}:${AMANAH_IP}:${AMANAH_SSH_PORT} \
    root@localhost -p 2222 \
    -N &

TUNNEL_PID=$!
echo "Tunnel PID: $TUNNEL_PID"
echo

echo "=== Kawung siap! ==="
echo
echo "Set env variable berikut sebelum jalankan deployment script:"
echo "  export local_tunnel_port=${LOCAL_TUNNEL_PORT}"
echo "  export username_amanah=<user-vm-amanah>"
echo "  export private_key_amanah=<path-ke-private-key-amanah>"
echo
echo "Flow:"
echo "  Laptop → localhost:${LOCAL_TUNNEL_PORT} → Kawung → ${AMANAH_IP}:${AMANAH_SSH_PORT}"
echo
echo "Untuk stop tunnel: kill $TUNNEL_PID"
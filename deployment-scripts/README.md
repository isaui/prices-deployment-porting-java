# PRICES Deployment Scripts

Bash CLI untuk deploy fullstack projects. Port dari Java pipeline.

## Quick Start

```bash
sudo ./dependencies.sh
./fullstack-deploy.sh --artifact project.zip --slug myapp
```

## Arguments

| Argument | Description |
|----------|-------------|
| `--artifact` | Path ke artifact zip (required) |
| `--slug` | Project identifier (required) |
| `--frontend-url` | Frontend URL, default: `<slug>.<PRICES_DOMAIN>` |
| `--backend-url` | Backend URL, default: `backend-<slug>.<PRICES_DOMAIN>` |
| `--custom-frontend-url` | Custom frontend URL tambahan |
| `--custom-backend-url` | Custom backend URL tambahan |
| `--expose-monitoring` | Expose monitoring endpoint |
| `--env-file` | Path ke .env file |
| `--env KEY=VALUE` | Env var tambahan (repeatable) |
| `--redeploy` | Preserve postgres data saat redeploy |
| `--dry-run` | Preview tanpa eksekusi |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PRICES_DOMAIN` | `skripsi.isacitra.com` | Parent domain untuk auto-generate URLs |
| `PRICES_DEPLOYMENTS_DIR` | `/var/prices/deployments` | Directory untuk extract artifacts |
| `PRICES_NGINX_CONFIG_DIR` | `/var/prices/nginx/conf.d` | Nginx config dir (host path, mounted ke container) |
| `PRICES_NGINX_CONTAINER` | `prices-nginx` | Nama nginx container |

## Artifact Structure

```
project.zip
├── frontend/           # package.json atau Dockerfile
├── backend/            # Dockerfile
└── docker-compose.yml  # Optional, jika ada dipakai langsung
```

## Pipeline Stages

1. **Extract** - Unzip artifact ke deployment dir
2. **Env** - Generate dan merge environment variables
3. **Prepare Dist** - Siapkan frontend/backend dist, generate Dockerfile
4. **Prepare Compose** - Generate docker-compose.yml dan .env
5. **Docker Run** - `docker compose up -d --build`
6. **Nginx** - Generate config dan reload nginx

## Files

```
deployment-scripts/
├── dependencies.sh       # Install unzip, openssl
├── fullstack-deploy.sh   # Main orchestrator
└── lib/
    ├── extract.sh
    ├── env.sh
    ├── prepare-dist.sh
    ├── prepare-compose.sh
    ├── docker-run.sh
    └── nginx.sh
```

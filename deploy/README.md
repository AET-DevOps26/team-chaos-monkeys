# FoundFlow — Azure VM Deploy (Bullet 1)

Minimal Terraform + bash setup to provision a single Azure VM and run the full
Compose stack on it. Throwaway: Ansible replaces the bash scripts in the next
iteration.

## What this provisions

- Resource group, VNet/subnet, NSG (SSH/HTTP/HTTPS), static public IP, NIC
- One Ubuntu 22.04 LTS VM (`Standard_B2s` by default — see warning below)
- SSH key auth (no password)

## What this does NOT do

- Build images on the VM. We push to GHCR from your laptop, the VM only pulls.
- Run Ollama. The 4 GB VM can't host the local LLM; genai-service is forced
  onto `GENAI_PROVIDER=openai` in the prod override.

## Sizing warning

`Standard_B2s` is 2 vCPU / 4 GB. With 7 Spring services + 7 Postgres + Prom +
Grafana + frontend, this is **borderline**. If you see OOM kills (`docker
compose ps` shows services restart-looping), bump `vm_size` to
`Standard_B4ms` (4 vCPU / 16 GB) in `terraform.tfvars` and `terraform apply`.

## Prerequisites

| Tool | Used for | Check |
|------|----------|-------|
| `az` (logged in) | Terraform's azurerm provider auth | `az account show` |
| `terraform >= 1.5` | provision | `terraform version` |
| `docker` + compose plugin | local build & push | `docker compose version` |
| SSH keypair at `~/.ssh/tum_devops_azure[.pub]` | VM access | `ls ~/.ssh/tum_devops_azure*` |
| GHCR Personal Access Token | image push | scope: `write:packages` |
| OpenAI API key | genai-service on the VM | — |

## From zero to running app

All commands run from `deploy/`.

### 1. Provision the VM

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars   # edit if you want non-default values
terraform init
terraform plan                                  # review!
terraform apply                                 # type yes to confirm
cd ..
```

Outputs include `public_ip` and a ready-to-paste `ssh_command`.

### 2. Add an SSH alias

```bash
./scripts/update-ssh-config.sh
```

Adds `Host tum-azure` to `~/.ssh/config`. After this, `ssh tum-azure` works
from anywhere.

### 3. Build + push images (one-time per code change)

Trigger the **Build and push images** workflow from the Actions tab:

> https://github.com/AET-DevOps26/team-chaos-monkeys/actions/workflows/build-and-push.yml
> → Run workflow → (defaults are fine) → Run

The workflow runs in-org on `ubuntu-24.04`, authenticates with `GITHUB_TOKEN`,
builds the 9 service images, and pushes them to
`ghcr.io/aet-devops26/team-chaos-monkeys/*:latest`.

**Why not from your laptop?** GHCR refuses `create_package` for our org from
a personal PAT, even with the nested namespace. Only `GITHUB_TOKEN` (and org
admins) can create new packages in the org — and only the former auto-links
the package back to this repo so package settings/visibility live in the right
place. `scripts/build-and-push.sh` is kept around for local image-build
debugging, but you can't use it to publish.

**First push only:** mark the nine packages public so the VM can pull without
a token. GitHub → this repo → Packages → each package → Package settings →
Change visibility → **Public**.

(If you'd rather keep them private, add `docker login ghcr.io` to
`bootstrap.sh` and stash a read-only PAT in env on the VM.)

### 4. Prepare `.env`

The repo's `.env` is gitignored. Copy from the example and fill in real values:

```bash
cd ..   # repo root
cp .env.example .env
# Edit .env, then add the line below (required on the VM, not local dev):
echo "OPENAI_API_KEY=sk-..." >> .env
cd deploy
```

### 5. Bootstrap the VM

```bash
./scripts/bootstrap.sh tum-azure
```

Installs Docker + compose plugin, ships `docker-compose.yml` + the prod
override + `.env`, pulls images, and starts the stack. Safe to re-run.

### 6. Verify

```bash
# Frontend (nginx serving the React bundle on host port 80)
curl -I http://$(terraform -chdir=terraform output -raw public_ip)/
# expect: HTTP/1.1 200 OK

# Or in a browser:
echo "http://$(terraform -chdir=terraform output -raw public_ip)/"

# Inspect the running stack
ssh tum-azure 'cd ~/foundflow && sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps'
```

## Required env vars (in `.env`)

Everything from `.env.example` (Postgres credentials × 7, `DEV_ADMIN_*`,
`GRAFANA_ADMIN_*`), plus on the VM:

- `OPENAI_API_KEY` — required (Ollama is excluded on the VM)
- `IMAGE_REGISTRY` — defaults to `ghcr.io/aet-devops26/team-chaos-monkeys`.
  Only set if you pushed images to a different registry.
- `GENAI_PROVIDER=openai` is set in the prod override; you don't need to set
  it in `.env`.

## Iterating

After a code change:

```bash
./scripts/build-and-push.sh                              # rebuild + push (laptop)
ssh tum-azure 'cd ~/foundflow && sudo docker compose \
  -f docker-compose.yml -f deploy/docker-compose.prod.yml pull && \
  sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d'
```

## Tearing down (stop burning credits)

```bash
cd terraform
terraform destroy
```

Everything goes — VM, disk, public IP, NSG, VNet, resource group. State file
stays so you can re-`apply` to recreate.

## File map

```
deploy/
├── README.md                      ← this file
├── docker-compose.prod.yml        ← VM-side overrides (image: from GHCR, no Ollama)
├── scripts/
│   ├── bootstrap.sh               ← install Docker + ship compose + start stack
│   ├── build-and-push.sh          ← local build + push to GHCR
│   └── update-ssh-config.sh       ← add `Host tum-azure` to ~/.ssh/config
└── terraform/
    ├── main.tf                    ← all resources, variables, outputs
    ├── terraform.tfvars.example   ← copy → terraform.tfvars (gitignored)
    └── .gitignore                 ← keeps state + tfvars out of git
```

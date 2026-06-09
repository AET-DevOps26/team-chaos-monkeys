# FoundFlow — Azure VM Deploy

Terraform provisions a single Azure VM; Ansible installs Docker and runs the
Compose stack on it. Both paths — local and CI — use the same playbook.

> The classic CD path is **`.github/workflows/azure-cycle.yml`**: a
> `workflow_dispatch` job that runs `terraform apply` → `ansible-playbook` (or
> `terraform destroy`). See "Deploy via CI" below. Local steps below remain
> supported.

## Teammates: quickest path to a running demo

No local tooling needed — just GitHub.

1. **Actions** tab → **Azure cycle (ephemeral VM)** → **Run workflow**
2. Pick `Use workflow from: development`, `action: apply`, leave `image_tag: latest`
3. Wait ~7 min — the run **Summary** prints the public IP and URLs
4. Visit `http://<ip>/`; log in with `admin@foundflow.local` / `admin12345`
5. When done, **re-run the workflow with `action: destroy`** — the VM is billed hourly even when idle (~$1.10/day)

Coordinate before dispatching: only one VM exists at a time, and concurrent `apply` runs queue against the same Terraform state. CLI equivalent:

```sh
gh workflow run azure-cycle.yml --ref development -f action=apply
# … wait for the run to finish, grab the IP from the Summary …
gh workflow run azure-cycle.yml --ref development -f action=destroy
```

## What this provisions

- Resource group, VNet/subnet, NSG (SSH/HTTP/HTTPS), static public IP, NIC
- One Ubuntu 22.04 LTS VM (`Standard_B2s_v2` by default — see warning below)
- SSH key auth (no password)

## What this does NOT do

- Build images on the VM. We push to GHCR from your laptop, the VM only pulls.
- Run Ollama. The 8 GB VM can't host the local LLM; genai-service is forced
  onto `GENAI_PROVIDER=openai` in the prod override.

## Sizing warning

`Standard_B2s_v2` is 2 vCPU / 8 GB / ~$0.05/hr. With 7 Spring services + 7
Postgres + RabbitMQ + MinIO + Prom + Grafana + frontend, this fits but leaves
little headroom. If you see OOM kills (`docker compose ps` shows services
restart-looping), bump `vm_size` to `Standard_B4ms` (4 vCPU / 16 GB) in
`terraform.tfvars` and `terraform apply`.

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

### 5. Run the playbook

```bash
cd ansible
export OPENAI_API_KEY=sk-...
export INTERNAL_SERVICE_TOKEN="$(openssl rand -hex 32)"
ansible-playbook \
  -i "$(terraform -chdir=../terraform output -raw public_ip)," \
  --user azureuser \
  --private-key ~/.ssh/tum_devops_azure \
  deploy.yml \
  -e "openai_api_key=$OPENAI_API_KEY"
cd ..
```

Installs Docker + compose plugin, rsyncs the repo, templates `.env` with
`OPENAI_API_KEY` and `INTERNAL_SERVICE_TOKEN`, pulls images, starts the stack,
and waits for the gateway to return 200. Re-running against the same VM is a
near-no-op when nothing changed.

> `scripts/bootstrap.sh` is the legacy bash path. Still works, kept as a
> one-shot debug helper — the Ansible playbook is the primary entrypoint.

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
- `INTERNAL_SERVICE_TOKEN` — required shared token for internal
  service-to-service endpoints.
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

### RabbitMQ queue renames

The matching consumers declare durable queues. If a deploy changes a durable
queue name on a broker with persistent storage, the old queue remains until it
is drained or deleted explicitly. For this branch, the found-item matching queue
was renamed from `matching.found-item-logged.v1` to
`matching.found-item-created.v1`; drain/delete the old queue on persistent
brokers after the rollout. For disposable local stacks, recreate the broker
volume with `docker compose down -v` before starting the stack again.

## Deploy via CI

Actions tab → **Azure cycle (ephemeral VM)** → Run workflow:

- `action: apply` — `terraform apply` + run the playbook against the new VM.
  Run summary prints the public IP and URLs.
- `action: destroy` — `terraform destroy`. Run this when you're done; the VM
  is billed by the hour even when idle.

Terraform state lives in the `tfstate` container of the
`foundflowtfstate720ff3` storage account (RG `tfstate-rg`), so `apply` and
`destroy` runs share state across workflow invocations.

Required repo secrets: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`,
`AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_VM_SSH_KEY` (private key),
`OPENAI_API_KEY`, `INTERNAL_SERVICE_TOKEN`.

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
├── ansible/
│   ├── ansible.cfg
│   ├── deploy.yml                 ← install Docker + sync repo + compose up
│   └── templates/env.j2           ← .env template (OPENAI_API_KEY injected)
├── scripts/
│   ├── bootstrap.sh               ← deprecated; one-shot debug helper
│   ├── build-and-push.sh          ← local build + push to GHCR
│   └── update-ssh-config.sh       ← add `Host tum-azure` to ~/.ssh/config
└── terraform/
    ├── main.tf                    ← all resources, variables, outputs (azurerm remote backend)
    ├── terraform.tfvars.example   ← copy → terraform.tfvars (gitignored)
    └── .gitignore                 ← keeps state + tfvars out of git
```

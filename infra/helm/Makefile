# FoundFlow local-k8s workflow.
# Targets run against your current kubectl context, which should point at the
# built-in Kubernetes of OrbStack or Docker Desktop. See docs/local-k8s.md.
#
# Common flow:
#   make cluster-bootstrap   # one-time: ingress-nginx + kube-prom-stack + ns
#   make build               # build all foundflow images
#   make helm-install        # install/upgrade the foundflow chart
#   make smoke               # health-check via local ingress
#   make logs                # tail app pods
#   make helm-uninstall      # tear down the release

REPO_ROOT      := $(shell git rev-parse --show-toplevel)
CHART_DIR      := $(REPO_ROOT)/infra/helm/foundflow
NAMESPACE      := team-chaos-monkeys
MONITORING_NS  := monitoring
INGRESS_NS     := ingress-nginx
RELEASE        := foundflow
LOCAL_HOST     := foundflow.localtest.me
IMAGE_TAG      := dev
IMAGE_PREFIX   := foundflow

SPRING_SERVICES := auth-service lost-item-service found-item-service \
                   matching-service notification-service operations-service

LOCAL_VALUES        := $(CHART_DIR)/values-local.yaml
LOCAL_SECRET_VALUES := $(CHART_DIR)/values-local.yaml.local
HELM_VALUES_ARGS    := -f $(LOCAL_VALUES) $(if $(wildcard $(LOCAL_SECRET_VALUES)),-f $(LOCAL_SECRET_VALUES))

.PHONY: help cluster-bootstrap build build-spring build-gateway build-genai build-client \
        deps helm-install helm-uninstall smoke logs status template lint

help:
	@echo "Targets:"
	@echo "  cluster-bootstrap  Install ingress-nginx + kube-prom-stack + create $(NAMESPACE)"
	@echo "  build              Build all foundflow images (foundflow/<svc>:$(IMAGE_TAG))"
	@echo "  deps               helm dependency update for the chart"
	@echo "  helm-install       helm upgrade --install foundflow with values-local.yaml"
	@echo "  helm-uninstall     Remove the release (keeps PVCs)"
	@echo "  smoke              curl ingress to verify the system is up"
	@echo "  logs               Tail app pod logs"
	@echo "  status             kubectl get pods,svc,ingress in $(NAMESPACE)"
	@echo "  template           helm template ... | less (no apply)"
	@echo "  lint               helm lint with both values files"

# ---------------------------------------------------------------------------
# Cluster bootstrap (runs against current kubectl context)
# ---------------------------------------------------------------------------

cluster-bootstrap:
	@echo "Bootstrapping against kubectl context: $$(kubectl config current-context)"
	@echo "Adding helm repos..."
	helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
	helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
	helm repo update
	@echo "Installing ingress-nginx..."
	# admissionWebhooks disabled locally: only useful in shared clusters and
	# causes "no endpoints" install failures whenever the controller pod is
	# briefly unready (cold start, disk pressure, etc.).
	helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
	  -n $(INGRESS_NS) --create-namespace \
	  --set controller.service.type=LoadBalancer \
	  --set controller.admissionWebhooks.enabled=false \
	  --set controller.resources.requests.cpu=50m \
	  --set controller.resources.requests.memory=64Mi \
	  --wait --timeout 3m
	kubectl -n $(INGRESS_NS) wait \
	  --for=condition=Ready pod \
	  -l app.kubernetes.io/component=controller --timeout=120s
	@echo "Installing kube-prometheus-stack (release name 'kps' matches values-local.yaml)..."
	# Disabled on Docker Desktop / OrbStack (cosmetic — these targets would
	# otherwise show DOWN in Grafana):
	#   - nodeExporter: rootfs isn't a shared mount; metrics would be from
	#     the LinuxKit/OrbStack VM, not your Mac.
	#   - kube{ControllerManager,Scheduler,Proxy,Etcd,Dns}: control-plane
	#     components aren't exposed on the kubeadm-style ports the chart
	#     expects (they're embedded/loopback-bound).
	helm upgrade --install kps prometheus-community/kube-prometheus-stack \
	  -n $(MONITORING_NS) --create-namespace \
	  --set grafana.enabled=false \
	  --set alertmanager.enabled=false \
	  --set nodeExporter.enabled=false \
	  --set kubeControllerManager.enabled=false \
	  --set kubeScheduler.enabled=false \
	  --set kubeProxy.enabled=false \
	  --set kubeEtcd.enabled=false \
	  --set kubeDns.enabled=false \
	  --set defaultRules.create=true \
	  --wait
	@echo "Creating namespace $(NAMESPACE)..."
	kubectl get ns $(NAMESPACE) >/dev/null 2>&1 || kubectl create ns $(NAMESPACE)

# ---------------------------------------------------------------------------
# Image build
# ---------------------------------------------------------------------------

build: build-gateway build-spring build-genai build-client

build-gateway:
	docker build -t $(IMAGE_PREFIX)/gateway-service:$(IMAGE_TAG) \
	  $(REPO_ROOT)/services/gateway-service

build-spring:
	@for svc in $(SPRING_SERVICES); do \
	  echo ">> Building $$svc"; \
	  docker build -t $(IMAGE_PREFIX)/$$svc:$(IMAGE_TAG) \
	    -f $(REPO_ROOT)/services/$$svc/Dockerfile $(REPO_ROOT) || exit 1; \
	done

build-genai:
	docker build -t $(IMAGE_PREFIX)/genai-service:$(IMAGE_TAG) \
	  $(REPO_ROOT)/services/genai-service

build-client:
	docker build -t $(IMAGE_PREFIX)/client:$(IMAGE_TAG) $(REPO_ROOT)/client

# ---------------------------------------------------------------------------
# Helm install / verify
# ---------------------------------------------------------------------------

deps:
	cd $(CHART_DIR) && helm dependency update

helm-install: deps
	helm upgrade --install $(RELEASE) $(CHART_DIR) \
	  -n $(NAMESPACE) \
	  $(HELM_VALUES_ARGS) \
	  --wait --timeout 10m

helm-uninstall:
	# Deletes Deployments, Services, Secrets, etc. — but NOT the PVCs created
	# by the Postgres StatefulSets (volumeClaimTemplates PVCs are intentionally
	# retained by Kubernetes to protect data). If you reinstall without also
	# deleting the PVCs, the auto-generated DB password drifts and Postgres
	# auth fails — see templates/databases.yaml header for the full footgun.
	# To fully reset state (drops all DB data):
	#   kubectl -n $(NAMESPACE) delete pvc -l app.kubernetes.io/part-of=foundflow
	helm uninstall $(RELEASE) -n $(NAMESPACE) || true

template:
	helm template $(RELEASE) $(CHART_DIR) -n $(NAMESPACE) $(HELM_VALUES_ARGS)

lint:
	helm lint $(CHART_DIR) -f $(LOCAL_VALUES)
	helm lint $(CHART_DIR) -f $(CHART_DIR)/values-aet.yaml

smoke:
	@echo ">> Client root:";    curl -sS http://$(LOCAL_HOST)/                  -o /dev/null -w "  HTTP %{http_code} (expect 200)\n"
	@echo ">> Gateway up:";     curl -sS http://$(LOCAL_HOST)/api/actuator/health -o /dev/null -w "  HTTP %{http_code} (expect 401 — Spring Cloud Gateway routes /api/** downstream; 401 proves the ingress→gateway→security chain is live. For a direct health probe, exec into the pod: kubectl -n $(NAMESPACE) exec deploy/gateway-service -- wget -qO- http://localhost:8080/actuator/health)\n"
	@echo ">> Grafana health:"; curl -sS http://$(LOCAL_HOST)/grafana/api/health -w "\n"

logs:
	kubectl -n $(NAMESPACE) logs -l app.kubernetes.io/part-of=foundflow --tail=50 --max-log-requests=20

status:
	@kubectl -n $(NAMESPACE) get pods,svc,ingress

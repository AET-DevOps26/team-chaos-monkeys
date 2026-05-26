{{/* vim: set filetype=mustache: */}}

{{/*
Common labels applied to every resource we render.
Use as: {{ include "foundflow.labels" . | nindent 4 }}
*/}}
{{- define "foundflow.labels" -}}
app.kubernetes.io/part-of: foundflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/*
Per-service labels. Argument is the service NAME (key from .Values.services), e.g. "auth-service".
*/}}
{{- define "foundflow.svcLabels" -}}
{{ include "foundflow.labels" .ctx }}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/component: {{ .name }}
{{- end -}}

{{/*
Selector for one service. Stable across upgrades.
*/}}
{{- define "foundflow.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: foundflow
app.kubernetes.io/instance: {{ .ctx.Release.Name }}
{{- end -}}

{{/*
Full image reference for a service. Args: { ctx, name, svc }
svc is the per-service values entry. Allows imageName/imageTag overrides per service.
*/}}
{{- define "foundflow.image" -}}
{{- $reg := .ctx.Values.global.imageRegistry -}}
{{- $img := default .name .svc.imageName -}}
{{- $tag := default .ctx.Values.global.imageTag .svc.imageTag -}}
{{- printf "%s/%s:%s" $reg $img $tag -}}
{{- end -}}

{{/*
Default HTTP probe path for a service. Args: { svc }
spring: /actuator/health   python: /health   static: /
Override with svc.healthPath.
*/}}
{{- define "foundflow.healthPath" -}}
{{- $svc := .svc -}}
{{- if $svc.healthPath -}}
{{ $svc.healthPath }}
{{- else if eq (default "spring" $svc.kind) "spring" -}}
/actuator/health
{{- else if eq $svc.kind "python" -}}
/health
{{- else -}}
/
{{- end -}}
{{- end -}}

{{/*
Default metrics path for a service. Args: { svc }
spring: /actuator/prometheus  python: /metrics
Override with svc.metricsPath.
*/}}
{{- define "foundflow.metricsPath" -}}
{{- $svc := .svc -}}
{{- if $svc.metricsPath -}}
{{ $svc.metricsPath }}
{{- else if eq (default "spring" $svc.kind) "spring" -}}
/actuator/prometheus
{{- else -}}
/metrics
{{- end -}}
{{- end -}}

{{/*
Render the env entries for a service. Args: { ctx, name, svc }
Combines:
  1. Literal env (svc.env)
  2. DB env block (when svc.dbRef set): SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD
  3. Secret-backed env (svc.secretEnv)
*/}}
{{- define "foundflow.env" -}}
{{- $svc := .svc -}}
{{- with $svc.env }}
{{- toYaml . }}
{{- end }}
{{- if $svc.dbRef }}
{{- $dbAlias := $svc.dbRef }}
{{- $dbCfg := index .ctx.Values.databases $dbAlias }}
{{- if not $dbCfg }}{{ fail (printf "Service %q references unknown dbRef %q (not in .Values.databases)" .name $dbAlias) }}{{ end }}
- name: SPRING_DATASOURCE_URL
  value: {{ printf "jdbc:postgresql://%s:5432/%s" $dbAlias $dbCfg.database | quote }}
- name: SPRING_DATASOURCE_USERNAME
  value: {{ $dbCfg.username | quote }}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ $dbAlias }}
      key: password
{{- end }}
{{- with $svc.secretEnv }}
{{- range . }}
- name: {{ .name }}
  valueFrom:
    secretKeyRef:
      name: {{ .secret }}
      key: {{ .key }}
      {{- if .optional }}
      optional: true
      {{- end }}
{{- end }}
{{- end }}
{{- end -}}

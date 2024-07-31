{{/*
Expand the name of the chart.
*/}}
{{- define "cosmotech-api.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "cosmotech-api.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "cosmotech-api.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "cosmotech-api.labels" -}}
owner: csm-platform
helm.sh/chart: {{ include "cosmotech-api.chart" . }}
{{ include "cosmotech-api.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "cosmotech-api.selectorLabels" -}}
app.kubernetes.io/name: {{ include "cosmotech-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Default Network policy
*/}}
{{- define "cosmotech-api.defaultNetworkPolicy" -}}
{{- if .Values.networkPolicy.enabled }}
networking/traffic-allowed: "yes"
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "cosmotech-api.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "cosmotech-api.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create Docker secrets for pulling images from a private container registry.
*/}}
{{- define "cosmotech-api.imagePullSecret" -}}
{{- printf "{\"auths\": {\"%s\": {\"auth\": \"%s\"}}}" .Values.imageCredentials.registry (printf "%s:%s" .Values.imageCredentials.username .Values.imageCredentials.password | b64enc) | b64enc }}
{{- end }}

{{/*
Create Docker secrets so Argo Workflows can pull images from a private container registry.
*/}}
{{- define "cosmotech-api.argo.imagePullSecret" -}}
{{- printf "{\"auths\": {\"%s\": {\"auth\": \"%s\"}}}" .Values.argo.imageCredentials.registry (printf "%s:%s" .Values.argo.imageCredentials.username .Values.argo.imageCredentials.password | b64enc) | b64enc }}
{{- end }}

{{/*
Context path:
This path will follow the pattern /{rootName}/{namespace|tenantId}/{apiVersion}
E.g:
- /cosmotech-api/myTenant/v1
- /cosmotech-api/myTenant/ if apiVersion is set to "latest"
*/}}
{{- define "cosmotech-api.contextPath" -}}
{{- if eq .Values.api.version "latest" }}
{{- if .Values.api.multiTenant }}
{{- printf "%s/%s/" (printf "%s" .Values.api.servletContextPath | trimSuffix "/" ) .Release.Namespace }}
{{- else }}
{{- printf "%s/" (printf "%s" .Values.api.servletContextPath | trimSuffix "/" ) }}
{{- end }}
{{- else }}
{{- if .Values.api.multiTenant }}
{{- printf "%s/%s/%s/" (printf "%s" .Values.api.servletContextPath | trimSuffix "/" ) .Release.Namespace (printf "%s" .Values.api.version | trimSuffix "/" ) }}
{{- else }}
{{- printf "%s/%s/" (printf "%s" .Values.api.servletContextPath | trimSuffix "/" ) (printf "%s" .Values.api.version | trimSuffix "/" ) }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Location of the persistence data
*/}}
{{- define "cosmotech-api.blobPersistencePath" -}}
"/var/lib/cosmotech-api/data"
{{- end }}

{{- define "cosmotech-api.custom-rootca-path" -}}
"/mnt/cosmotech/certificates/{{ .Values.api.tlsTruststore.fileName }}"
{{- end }}

{{- define "cosmotech-api.custom-rootca-bundle" -}}
"custom-rootca"
{{- end }}

{{- define "cosmotech-api.baseConfig" -}}
spring:
  application:
    name: {{ include "cosmotech-api.fullname" . }}
  output:
    ansi:
      enabled: never
{{/*
ssl bundle must be set here because it is searched even if empty
*/}}
{{- if and .Values.api.tlsTruststore.enabled }}
  data:
    redis:
      ssl:
        bundle: {{ include "cosmotech-api.custom-rootca-bundle" . }}
{{- end }}
{{- if and .Values.api.tlsTruststore.enabled (eq .Values.api.tlsTruststore.type "pem") }}
  ssl:
    bundle:
      pem:
        {{ include "cosmotech-api.custom-rootca-bundle" . }}:
          truststore:
            certificate: {{ include "cosmotech-api.custom-rootca-path" . }}
{{- end }}
{{- if and .Values.api.tlsTruststore.enabled (eq .Values.api.tlsTruststore.type "jks") }}
  ssl:
    bundle:
      jks:
        {{ include "cosmotech-api.custom-rootca-bundle" . }}:
          truststore:
            location: {{ include "cosmotech-api.custom-rootca-path" . }}
            password: {{ .Values.api.tlsTruststore.jksPassword }}
{{- end }}

api:
  version: "{{ .Values.api.version }}"
  multiTenant: {{ default true .Values.api.multiTenant }}
  servletContextPath: {{ include "cosmotech-api.contextPath" . }}

server:
  servlet:
    context-path: {{ include "cosmotech-api.contextPath" . }}
  undertow:
    accesslog:
      enabled: false

management:
  endpoint:
    health:
      show-details: always
      probes:
        # This exposes the following endpoints for Kubernetes: /actuator/health/{live,readi}ness
        enabled: true
      group:
        readiness:
          include: "readinessState,runArgo"

csm:
  platform:
    api:
      base-url: "http://{{ include "cosmotech-api.fullname" . }}.{{ .Release.Namespace }}.svc.cluster.local:{{ .Values.service.port }}{{ include "cosmotech-api.contextPath" . | trimSuffix "/" }}"
      # API Base Path for OpenAPI-generated controllers.
      # Might conflict with the SpringBoot context path, hence leaving it at the root
      base-path: /
    argo:
      {{- if .Values.argo.imageCredentials.registry }}
      image-pull-secrets:
        - {{ include "cosmotech-api.fullname" . }}-workflow-registry
      {{- else }}
      image-pull-secrets: []
      {{- end }}
    blobPersistence:
      path: {{ include "cosmotech-api.blobPersistencePath" . }}
    {{- if and .Values.api.tlsTruststore.enabled .Values.config.csm.platform.twincache.tls.enabled }}
    twinCache:
      tls:
        bundle: {{ include "cosmotech-api.custom-rootca-bundle" . }}
    {{- end }}
{{- end }}

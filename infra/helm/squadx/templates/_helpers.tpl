{{/*
Expand the name of the chart.
*/}}
{{- define "squadx.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "squadx.fullname" -}}
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
Chart label
*/}}
{{- define "squadx.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "squadx.labels" -}}
helm.sh/chart: {{ include "squadx.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: squadx
{{- end }}

{{/*
Selector labels for a component
Usage: {{ include "squadx.selectorLabels" (dict "context" . "component" "backend") }}
*/}}
{{- define "squadx.selectorLabels" -}}
app.kubernetes.io/name: {{ include "squadx.name" .context }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Component labels (common + selector)
Usage: {{ include "squadx.componentLabels" (dict "context" . "component" "backend") }}
*/}}
{{- define "squadx.componentLabels" -}}
{{ include "squadx.labels" .context }}
{{ include "squadx.selectorLabels" (dict "context" .context "component" .component) }}
{{- end }}

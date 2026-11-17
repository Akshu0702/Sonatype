{{- define "mls-service.name" -}}
{{- default .Release.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "mls-service.labels" -}}
app.kubernetes.io/name: {{ include "mls-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: mls
{{- end -}}

{{- define "mls-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mls-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "mls-service.image" -}}
{{- $repo := required "image.repository is required" .Values.image.repository -}}
{{- if .Values.image.digest -}}
{{ $repo }}@{{ .Values.image.digest }}
{{- else -}}
{{ $repo }}:{{ required "image.tag is required (the promote step sets it)" .Values.image.tag }}
{{- end -}}
{{- end -}}

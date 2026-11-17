variable "name_prefix" {
  description = "Prefix for the IAM role names: <name_prefix>-<component>, for example mls-role-prod-lab gives mls-role-prod-lab-ebs-csi."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,40}$", var.name_prefix))
    error_message = "name_prefix must be lower case letters, digits and hyphens, starting with a letter."
  }
}

variable "cluster_name" {
  description = "EKS cluster name (from the eks-cluster module). Used in the cluster autoscaler's permissions."
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the cluster's OIDC provider (eks-cluster output oidc_provider_arn)."
  type        = string
}

variable "oidc_issuer" {
  description = "OIDC issuer URL without the scheme (eks-cluster output oidc_issuer)."
  type        = string

  validation {
    condition     = !startswith(var.oidc_issuer, "https://")
    error_message = "oidc_issuer must not include https://."
  }
}

variable "service_accounts" {
  description = "Namespace and name of each add-on's Kubernetes service account. The Helm values must use the same names."
  type = object({
    ebs_csi                  = optional(object({ namespace = optional(string, "kube-system"), name = optional(string, "ebs-csi-controller-sa") }), {})
    load_balancer_controller = optional(object({ namespace = optional(string, "kube-system"), name = optional(string, "aws-load-balancer-controller") }), {})
    cluster_autoscaler       = optional(object({ namespace = optional(string, "kube-system"), name = optional(string, "cluster-autoscaler") }), {})
    external_secrets         = optional(object({ namespace = optional(string, "external-secrets"), name = optional(string, "external-secrets") }), {})
  })
  default = {}
}

# ---------------------------------------------------------------- EBS CSI driver
variable "enable_ebs_csi" {
  description = "Create the EBS CSI driver role and install the managed add-on, so gp3 volumes can be provisioned."
  type        = bool
  default     = true
}

variable "ebs_kms_key_arns" {
  description = "Customer-managed KMS keys that encrypt EBS volumes. Required when the EBS CSI driver is enabled."
  type        = list(string)
  default     = []

  validation {
    condition     = !var.enable_ebs_csi || length(var.ebs_kms_key_arns) > 0
    error_message = "ebs_kms_key_arns must list at least one key when enable_ebs_csi is true."
  }
}

variable "ebs_csi_addon_version" {
  description = "Version of the EBS CSI driver add-on. Null lets EKS choose the default for the cluster version."
  type        = string
  default     = null
}

# ---------------------------------------------------------------- Load Balancer Controller, autoscaler
variable "enable_load_balancer_controller" {
  description = "Create the AWS Load Balancer Controller role, using the controller's official IAM policy."
  type        = bool
  default     = true
}

variable "enable_cluster_autoscaler" {
  description = "Create the Cluster Autoscaler role."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------- External Secrets
variable "enable_external_secrets" {
  description = "Create the External Secrets role, which reads Secrets Manager secrets for the cluster."
  type        = bool
  default     = true
}

variable "external_secrets_secret_arns" {
  description = "Secret ARNs or ARN patterns the cluster may read, for example arn:aws:secretsmanager:ap-south-1:111111111111:secret:mls/prod/*. Required when External Secrets is enabled."
  type        = list(string)
  default     = []

  validation {
    condition     = !var.enable_external_secrets || length(var.external_secrets_secret_arns) > 0
    error_message = "external_secrets_secret_arns must list at least one secret or pattern when enable_external_secrets is true."
  }

  validation {
    condition     = alltrue([for a in var.external_secrets_secret_arns : a != "*" && startswith(a, "arn:")])
    error_message = "Each entry must be an ARN or ARN pattern. A bare * would give the cluster every secret."
  }
}

variable "external_secrets_kms_key_arns" {
  description = "KMS keys that encrypt those secrets, so the role can decrypt them."
  type        = list(string)
  default     = []

  validation {
    condition     = !var.enable_external_secrets || length(var.external_secrets_kms_key_arns) > 0
    error_message = "external_secrets_kms_key_arns must list at least one key when enable_external_secrets is true."
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}

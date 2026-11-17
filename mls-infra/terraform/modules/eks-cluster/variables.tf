variable "name" {
  description = "Cluster name, from the naming module, for example mls-eks-prod-lab-001."
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version, for example 1.31. Deliberately has no default: the version is a decision, pinned by the caller."
  type        = string

  validation {
    condition     = can(regex("^1\\.[0-9]{2}$", var.kubernetes_version))
    error_message = "kubernetes_version must look like 1.31."
  }
}

variable "subnet_ids" {
  description = "Private subnets for the control plane network interfaces, in at least two availability zones."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "Give subnets in at least two availability zones."
  }
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key for Kubernetes secrets and node volumes."
  type        = string
}

variable "additional_security_group_ids" {
  description = "Extra security groups attached to the control plane network interfaces."
  type        = list(string)
  default     = []
}

variable "enabled_log_types" {
  description = "Control plane log types sent to CloudWatch. All five by default, for the audit trail."
  type        = list(string)
  default     = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
}

variable "service_ipv4_cidr" {
  description = "Service address range. Null lets EKS choose."
  type        = string
  default     = null
}

variable "access_entries" {
  description = <<-EOT
    Who may use the cluster, by name. The cluster uses EKS access entries only (no aws-auth ConfigMap) and the
    creator is NOT made an administrator, so at least one entry must be a cluster administrator or nobody could
    use the cluster. The principal ARN is an IAM role, for example the Identity Center administrator role.
  EOT
  type = map(object({
    principal_arn     = string
    policy_arn        = optional(string, "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy")
    access_scope_type = optional(string, "cluster")
    namespaces        = optional(list(string), [])
  }))

  validation {
    condition = anytrue([
      for k, e in var.access_entries :
      endswith(e.policy_arn, "/AmazonEKSClusterAdminPolicy") && e.access_scope_type == "cluster"
    ])
    error_message = "At least one access entry must be a cluster-wide administrator (AmazonEKSClusterAdminPolicy, scope cluster)."
  }

  validation {
    condition     = alltrue([for k, e in var.access_entries : contains(["cluster", "namespace"], e.access_scope_type)])
    error_message = "access_scope_type must be cluster or namespace."
  }
}

variable "node_groups" {
  description = <<-EOT
    Managed node groups, by name. For workloads with zonal volumes (the Artifactory StatefulSet), make one group
    per availability zone with a single subnet each. Nodes use IMDSv2 only and encrypted gp3 root volumes.
  EOT
  type = map(object({
    subnet_ids     = list(string)
    instance_types = list(string)
    min_size       = number
    desired_size   = number
    max_size       = number
    disk_size_gb   = optional(number, 100)
    ami_type       = optional(string, "AL2023_x86_64_STANDARD")
    capacity_type  = optional(string, "ON_DEMAND")
    labels         = optional(map(string), {})
    taints = optional(list(object({
      key    = string
      value  = optional(string)
      effect = string
    })), [])
  }))
  default = {}

  validation {
    condition     = alltrue([for k, g in var.node_groups : g.min_size <= g.desired_size && g.desired_size <= g.max_size && g.max_size >= 1])
    error_message = "Each node group needs min_size <= desired_size <= max_size, and max_size of at least 1."
  }

  validation {
    condition     = alltrue([for k, g in var.node_groups : startswith(g.ami_type, "AL2023_")])
    error_message = "ami_type must be an Amazon Linux 2023 type (AL2023_*). The node hardening is written for AL2023; another operating system needs its own hardening before it is allowed here."
  }

  validation {
    condition     = alltrue([for k, g in var.node_groups : contains(["ON_DEMAND", "SPOT"], g.capacity_type)])
    error_message = "capacity_type must be ON_DEMAND or SPOT."
  }

  validation {
    condition     = alltrue([for k, g in var.node_groups : alltrue([for t in g.taints : contains(["NO_SCHEDULE", "NO_EXECUTE", "PREFER_NO_SCHEDULE"], t.effect)])])
    error_message = "A taint effect must be NO_SCHEDULE, NO_EXECUTE or PREFER_NO_SCHEDULE."
  }
}

variable "cluster_addons" {
  description = <<-EOT
    Managed add-ons, by name. The three core add-ons are on by default. The EBS CSI driver and others that need
    an IAM role for a service account are added by the caller with service_account_role_arn.
  EOT
  type = map(object({
    version                  = optional(string)
    service_account_role_arn = optional(string)
    resolve_conflicts        = optional(string, "OVERWRITE")
  }))
  default = {
    vpc-cni    = {}
    coredns    = {}
    kube-proxy = {}
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}

variable "node_hardening" {
  description = <<-EOT
    Boot-time hardening of the worker nodes (Amazon Linux 2023 managed node groups), written as launch template user data:
    kubelet settings through nodeadm, kernel settings, and unused kernel modules disabled. On by default.
    protect_kernel_defaults and server_tls_bootstrap are off by default because each needs something outside this module:
    see the README before turning them on.
  EOT
  type = object({
    enabled                 = optional(bool, true)
    seccomp_default         = optional(bool, true)
    pod_pids_limit          = optional(number, 4096)
    protect_kernel_defaults = optional(bool, false)
    server_tls_bootstrap    = optional(bool, false)
    disabled_kernel_modules = optional(list(string), ["cramfs", "freevxfs", "hfs", "hfsplus", "jffs2", "udf", "usb-storage", "dccp", "sctp", "rds", "tipc"])
    extra_sysctls           = optional(map(string), {})
  })
  default = {}

  validation {
    condition     = var.node_hardening.pod_pids_limit >= 0
    error_message = "pod_pids_limit must be zero (no limit) or more."
  }

  validation {
    condition     = alltrue([for m in var.node_hardening.disabled_kernel_modules : can(regex("^[a-z0-9_-]+$", m))])
    error_message = "A kernel module name may contain only lower-case letters, digits, underscores and hyphens."
  }

  validation {
    condition     = alltrue([for k, val in var.node_hardening.extra_sysctls : can(regex("^[a-z0-9_.]+$", k)) && can(regex("^[0-9]+$", val))])
    error_message = "extra_sysctls keys may contain only lower-case letters, digits, dots and underscores, and values must be whole numbers."
  }
}

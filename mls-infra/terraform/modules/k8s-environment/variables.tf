variable "environment" {
  description = "The environment this composition builds: uat or prod."
  type        = string

  validation {
    condition     = contains(["uat", "prod"], var.environment)
    error_message = "environment must be uat or prod. Dev and QA run on standalone virtual machines, not here."
  }
}

variable "region" {
  description = "The only region used."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "Only ap-south-1 (Mumbai) is allowed."
  }
}

variable "owner" {
  description = "Team that owns the resources. Never a person."
  type        = string
  default     = "platform"
}

variable "cluster_mode" {
  description = <<-EOT
    dedicated: this environment gets its own VPC and EKS cluster (the default: a fault, an upgrade or a mistake in UAT cannot reach Prod).
    shared: this environment reuses an existing cluster and VPC, isolated by namespaces; it still gets its own database, secrets,
    registry bucket and keys. Shared mode needs var.shared_cluster and creates no VPC, cluster or add-ons.
  EOT
  type        = string
  default     = "dedicated"

  validation {
    condition     = contains(["dedicated", "shared"], var.cluster_mode)
    error_message = "cluster_mode must be dedicated or shared."
  }
}

variable "shared_cluster" {
  description = "The existing cluster to reuse when cluster_mode is shared. external_secrets_role_name, when given, receives read access to this environment's secrets."
  type = object({
    cluster_name               = string
    vpc_id                     = string
    data_subnet_ids            = list(string)
    node_security_group_id     = string
    oidc_provider_arn          = string
    oidc_issuer                = string
    external_secrets_role_name = optional(string)
  })
  default = null

  validation {
    condition     = var.cluster_mode == "dedicated" || var.shared_cluster != null
    error_message = "cluster_mode shared needs shared_cluster."
  }
}

variable "cidr_block" {
  description = "VPC range for a dedicated cluster. Must not overlap 10.40.0.0/13 or 10.48.0.0/13 plans already in use (see the network plan)."
  type        = string
  default     = null
}

variable "azs" {
  description = "Availability zones: two for UAT, three for Prod."
  type        = list(string)
  default     = []

  validation {
    condition     = length(var.azs) == 0 || (length(var.azs) >= 2 && length(var.azs) <= 3)
    error_message = "Use two or three availability zones."
  }
}

variable "enable_nat" {
  description = "One NAT gateway per zone so nodes reach Azure DevOps (Argo CD), the Maven feed and public chart repositories. The access is outbound only; there is no public endpoint."
  type        = bool
  default     = true
}

variable "kubernetes_version" {
  description = "Kubernetes version. No default: the version is a decision, pinned by the caller."
  type        = string
  default     = null
}

variable "access_entries" {
  description = "Who may use the cluster (EKS access entries). At least one cluster administrator. Same shape as the eks-cluster module."
  type = map(object({
    principal_arn     = string
    policy_arn        = optional(string, "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy")
    access_scope_type = optional(string, "cluster")
    namespaces        = optional(list(string), [])
  }))
  default = {}
}

variable "node_groups" {
  description = "Managed node groups. Each group spans every node subnet of the environment."
  type = map(object({
    instance_types = list(string)
    min_size       = number
    desired_size   = number
    max_size       = number
    disk_size_gb   = optional(number, 100)
    capacity_type  = optional(string, "ON_DEMAND")
    labels         = optional(map(string), {})
  }))
  default = {}
}

variable "db" {
  description = "The environment's PostgreSQL instance (Amazon RDS). engine_version is pinned by the caller; multi_az is on in Prod only."
  type = object({
    engine_version          = string
    instance_class          = optional(string, "db.m6g.large")
    allocated_storage       = optional(number, 100)
    max_allocated_storage   = optional(number, 500)
    multi_az                = optional(bool, false)
    backup_retention_period = optional(number, 14)
    deletion_protection     = optional(bool, true)
  })
  default = null
}

variable "harbor" {
  description = "Harbor, the container registry that runs in this cluster. Its image data lives in an S3 bucket created here and its metadata in the database."
  type = object({
    namespace                  = optional(string, "harbor")
    registry_service_account   = optional(string, "harbor-registry")
    noncurrent_expiration_days = optional(number, 30)
  })
  default = {}
}

variable "tags" {
  description = "Extra tags. The standard tags always win."
  type        = map(string)
  default     = {}
}

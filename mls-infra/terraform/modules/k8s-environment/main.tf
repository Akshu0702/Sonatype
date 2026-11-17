data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  dedicated  = var.cluster_mode == "dedicated"
  zones      = min(3, length(var.azs))
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition
  prefix     = "mls-${var.environment}"
  pg_major   = split(".", try(var.db.engine_version, "16.0"))[0]
  extra_tags = merge(var.tags, { "k8s-environment" = var.environment })

  # Where the environment's things sit, whether the cluster is its own or shared.
  vpc_id          = local.dedicated ? one(module.vpc[*].vpc_id) : try(var.shared_cluster.vpc_id, null)
  data_subnet_ids = local.dedicated ? try(one(module.vpc[*].subnet_ids)["data"], []) : try(var.shared_cluster.data_subnet_ids, [])
  node_subnet_ids = local.dedicated ? try(one(module.vpc[*].subnet_ids)["nodes"], []) : []
  node_sg_id      = local.dedicated ? one(module.eks[*].cluster_security_group_id) : try(var.shared_cluster.node_security_group_id, null)
  cluster_name    = local.dedicated ? one(module.eks[*].cluster_name) : try(var.shared_cluster.cluster_name, null)
  oidc_arn        = local.dedicated ? one(module.eks[*].oidc_provider_arn) : try(var.shared_cluster.oidc_provider_arn, null)
  oidc_issuer     = local.dedicated ? one(module.eks[*].oidc_issuer) : try(var.shared_cluster.oidc_issuer, null)

  secret_arn_pattern = "arn:${local.partition}:secretsmanager:${var.region}:${local.account_id}:secret:mls/${var.environment}/*"

  # Secrets are created empty. Values are set by an operator, by the service, or by RDS (never by Terraform).
  secret_keys = {
    "tenants/db-password"      = "Password of the tenants service's database user"
    "itsm/db-password"         = "Password of the ticketing service's database user"
    "harbor/db-password"       = "Password of Harbor's database user"
    "harbor/admin-password"    = "Harbor administrator password"
    "harbor/secret-key"        = "Harbor core encryption key (16 characters)"
    "harbor/ci-robot"          = "Harbor robot account the build pipeline uses to push images"
    "harbor/pull-robot"        = "Harbor robot account the cluster uses to pull images"
    "argocd/repo-credentials"  = "Read-only credential for the Git repository Argo CD watches"
    "platform/keycloak-client" = "Client secret the services use to reach the identity provider"
  }
}

# ---------------------------------------------------------------- names
module "name_vpc" {
  source = "../naming"

  resource_type = "vpc"
  environment   = var.environment
  function      = "api"
  owner         = var.owner
  region        = var.region
  extra_tags    = local.extra_tags
}

module "name_eks" {
  source = "../naming"

  resource_type = "eks"
  environment   = var.environment
  function      = "api"
  owner         = var.owner
  region        = var.region
  extra_tags    = local.extra_tags
}

module "name_rds" {
  source = "../naming"

  resource_type = "rds"
  environment   = var.environment
  function      = "db"
  owner         = var.owner
  region        = var.region
  data_class    = "tenant-data"
  extra_tags    = local.extra_tags
}

module "name_registry" {
  source = "../naming"

  resource_type = "s3"
  environment   = var.environment
  function      = "store"
  owner         = var.owner
  region        = var.region
  data_class    = "internal"
  extra_tags    = local.extra_tags
}

# ---------------------------------------------------------------- keys
module "kms" {
  source = "../kms-keys"

  name_prefix = local.prefix
  tags        = module.name_vpc.tags

  keys = {
    eks     = { description = "Kubernetes secrets of the ${var.environment} cluster" }
    ebs     = { description = "Volumes of the ${var.environment} cluster (nodes and persistent volumes)" }
    rds     = { description = "The ${var.environment} PostgreSQL instance, its backups and its master secret" }
    s3      = { description = "The ${var.environment} registry image store" }
    secrets = { description = "Secrets Manager secrets of ${var.environment}" }
  }
}

# ---------------------------------------------------------------- network and cluster (dedicated mode only)
module "vpc" {
  source = "../network-vpc"
  count  = local.dedicated ? 1 : 0

  name       = module.name_vpc.name
  cidr_block = var.cidr_block
  azs        = var.azs
  tags       = module.name_vpc.tags

  # No inbound path from the internet: the public tier only carries the NAT gateways. Users reach the environment over the VPN.
  tiers = {
    nodes  = { newbits = 3, netnums = slice([0, 1, 2], 0, local.zones) }
    data   = { newbits = 5, netnums = slice([16, 17, 18], 0, local.zones) }
    tgw    = { newbits = 8, netnums = slice([192, 193, 194], 0, local.zones) }
    public = { newbits = 7, netnums = slice([104, 105, 106], 0, local.zones), public = true }
  }

  enable_nat       = var.enable_nat
  nat_routed_tiers = ["nodes", "data"]
}

module "eks" {
  source = "../eks-cluster"
  count  = local.dedicated ? 1 : 0

  name               = module.name_eks.name
  tags               = module.name_eks.tags
  kubernetes_version = var.kubernetes_version
  subnet_ids         = module.vpc[0].subnet_ids["nodes"]
  kms_key_arn        = module.kms.key_arns["eks"]
  access_entries     = var.access_entries

  node_groups = {
    for name, g in var.node_groups : name => {
      subnet_ids     = module.vpc[0].subnet_ids["nodes"]
      instance_types = g.instance_types
      min_size       = g.min_size
      desired_size   = g.desired_size
      max_size       = g.max_size
      disk_size_gb   = g.disk_size_gb
      capacity_type  = g.capacity_type
      labels         = g.labels
    }
  }
}

module "addons" {
  source = "../eks-addons"
  count  = local.dedicated ? 1 : 0

  name_prefix       = "mls-role-${var.environment}-api"
  cluster_name      = module.eks[0].cluster_name
  oidc_provider_arn = module.eks[0].oidc_provider_arn
  oidc_issuer       = module.eks[0].oidc_issuer
  tags              = module.name_eks.tags

  ebs_kms_key_arns              = [module.kms.key_arns["ebs"]]
  external_secrets_secret_arns  = [local.secret_arn_pattern]
  external_secrets_kms_key_arns = [module.kms.key_arns["secrets"]]
}

# In shared mode the cluster's External Secrets role also gets to read this environment's secrets.
resource "aws_iam_role_policy" "shared_external_secrets" {
  count = !local.dedicated && try(var.shared_cluster.external_secrets_role_name, null) != null ? 1 : 0

  name = "mls-${var.environment}-secrets-read"
  role = var.shared_cluster.external_secrets_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadEnvironmentSecrets"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
        Resource = [local.secret_arn_pattern]
      },
      {
        Sid      = "DecryptEnvironmentSecrets"
        Effect   = "Allow"
        Action   = ["kms:Decrypt", "kms:DescribeKey"]
        Resource = [module.kms.key_arns["secrets"]]
      },
    ]
  })
}

# ---------------------------------------------------------------- database
module "rds" {
  source = "../rds-postgres"

  identifier             = module.name_rds.name
  engine_version         = var.db.engine_version
  parameter_group_family = "postgres${local.pg_major}"
  instance_class         = var.db.instance_class
  allocated_storage      = var.db.allocated_storage
  max_allocated_storage  = var.db.max_allocated_storage
  kms_key_id             = module.kms.key_arns["rds"]

  vpc_id                     = local.vpc_id
  subnet_ids                 = local.data_subnet_ids
  allowed_security_group_ids = [local.node_sg_id]

  multi_az                = var.db.multi_az
  backup_retention_period = var.db.backup_retention_period
  deletion_protection     = var.db.deletion_protection
  tags                    = module.name_rds.tags
}

# ---------------------------------------------------------------- registry storage and identity (Harbor itself is installed by Argo CD)
module "registry_bucket" {
  source = "../s3-bucket"

  bucket_name        = module.name_registry.name
  kms_key_arn        = module.kms.key_arns["s3"]
  versioning_enabled = true
  tags               = module.name_registry.tags

  lifecycle_rules = [
    { id = "trim-old-versions", noncurrent_expiration_days = var.harbor.noncurrent_expiration_days },
  ]
}

resource "aws_iam_role" "harbor" {
  name = "mls-role-${var.environment}-harbor"
  tags = module.name_registry.tags

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_issuer}:aud" = "sts.amazonaws.com"
          "${local.oidc_issuer}:sub" = "system:serviceaccount:${var.harbor.namespace}:${var.harbor.registry_service_account}"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "harbor" {
  name = "registry-store"
  role = aws_iam_role.harbor.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ListTheStore"
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation", "s3:ListBucketMultipartUploads"]
        Resource = [module.registry_bucket.bucket_arn]
      },
      {
        Sid      = "ReadAndWriteImages"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
        Resource = ["${module.registry_bucket.bucket_arn}/*"]
      },
      {
        Sid      = "UseTheStoreKey"
        Effect   = "Allow"
        Action   = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey", "kms:DescribeKey"]
        Resource = [module.kms.key_arns["s3"]]
      },
    ]
  })
}

# ---------------------------------------------------------------- secrets (empty; values are set by hand or by the service)
module "secrets" {
  source = "../secrets"

  environment = var.environment
  tags        = module.name_rds.tags

  secrets = {
    for key, description in local.secret_keys : key => {
      description = description
      kms_key_id  = module.kms.key_arns["secrets"]
    }
  }
}

# ---------------------------------------------------------------- input checks that need more than one variable
resource "terraform_data" "inputs" {
  input = var.environment

  lifecycle {
    precondition {
      condition     = var.db != null
      error_message = "db is required: the database engine version is pinned by the caller."
    }

    precondition {
      condition     = !local.dedicated || (var.cidr_block != null && length(var.azs) >= 2 && var.kubernetes_version != null && length(var.node_groups) > 0 && length(var.access_entries) > 0)
      error_message = "A dedicated cluster needs cidr_block, azs, kubernetes_version, node_groups and access_entries."
    }

    precondition {
      condition     = var.environment != "prod" || (var.db != null && try(var.db.multi_az, false) && length(var.azs) == 3)
      error_message = "Prod needs three availability zones and a Multi-AZ database."
    }
  }
}

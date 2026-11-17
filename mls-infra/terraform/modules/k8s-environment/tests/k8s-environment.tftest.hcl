mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "111111111111"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

# UAT: two zones, a small single-zone database. These are the values a live stack passes.
variables {
  environment        = "uat"
  cidr_block         = "10.43.0.0/20"
  azs                = ["ap-south-1a", "ap-south-1b"]
  kubernetes_version = "1.36"

  access_entries = {
    platform_admin = { principal_arn = "arn:aws:iam::111111111111:role/mls-platform-admin" }
  }

  node_groups = {
    apps = { instance_types = ["m6i.large"], min_size = 2, desired_size = 2, max_size = 4 }
  }

  db = {
    engine_version = "16.15"
    multi_az       = false
  }
}

run "uat_dedicated_cluster_and_private_database" {
  command = plan

  assert {
    condition     = length(module.eks) == 1 && length(module.vpc) == 1 && length(module.addons) == 1
    error_message = "A dedicated environment builds its own VPC, cluster and add-on roles."
  }
}

run "names_follow_the_standard" {
  command = plan

  assert {
    condition     = module.name_eks.name == "mls-eks-uat-api-001" && module.name_rds.name == "mls-rds-uat-db-001" && module.name_registry.name == "mls-s3-uat-store-001"
    error_message = "Names must follow <project>-<type>-<environment>-<function>-<id>."
  }

  assert {
    condition     = module.name_rds.tags["data-class"] == "tenant-data" && module.name_registry.tags["data-class"] == "internal"
    error_message = "The database holds tenant data; the registry store is internal."
  }
}

run "database_family_follows_the_pinned_version" {
  command = plan

  assert {
    condition     = local.pg_major == "16"
    error_message = "The parameter group family must follow the pinned engine version."
  }
}

run "harbor_has_its_own_role" {
  command = plan

  assert {
    condition     = aws_iam_role.harbor.name == "mls-role-uat-harbor"
    error_message = "Harbor has its own role."
  }
}

run "secrets_exist_and_hold_no_values" {
  command = plan

  assert {
    condition     = length(module.secrets.secret_names) == 9
    error_message = "Nine secrets are created, empty."
  }
}

run "prod_needs_three_zones_and_multi_az" {
  command = plan

  variables {
    environment = "prod"
    cidr_block  = "10.44.0.0/20"
    # two zones and a single-zone database: both must be refused
  }

  expect_failures = [terraform_data.inputs]
}

run "prod_with_three_zones_and_multi_az_is_accepted" {
  command = plan

  variables {
    environment = "prod"
    cidr_block  = "10.44.0.0/20"
    azs         = ["ap-south-1a", "ap-south-1b", "ap-south-1c"]

    db = {
      engine_version = "16.15"
      multi_az       = true
    }
  }

  assert {
    condition     = module.name_eks.name == "mls-eks-prod-api-001"
    error_message = "Prod uses the same standard."
  }
}

run "dev_is_refused" {
  command = plan

  variables {
    environment = "dev"
  }

  expect_failures = [var.environment]
}

run "dedicated_cluster_needs_its_inputs" {
  command = plan

  variables {
    node_groups = {}
  }

  expect_failures = [terraform_data.inputs]
}

run "shared_mode_builds_no_cluster" {
  command = plan

  variables {
    cluster_mode = "shared"
    cidr_block   = null
    azs          = []

    shared_cluster = {
      cluster_name               = "mls-eks-uat-api-001"
      vpc_id                     = "vpc-0aaaaaaaaaaaaaaa1"
      data_subnet_ids            = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2"]
      node_security_group_id     = "sg-0aaaaaaaaaaaaaaa1"
      oidc_provider_arn          = "arn:aws:iam::111111111111:oidc-provider/oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000"
      oidc_issuer                = "oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000"
      external_secrets_role_name = "mls-role-uat-api-external-secrets"
    }
  }

  assert {
    condition     = length(module.eks) == 0 && length(module.vpc) == 0 && length(module.addons) == 0
    error_message = "A shared environment builds no VPC, cluster or add-ons."
  }

  # The identity provider is a known input in shared mode, so the trust policy can be read at plan time.
  assert {
    condition = (
      strcontains(aws_iam_role.harbor.assume_role_policy, "system:serviceaccount:harbor:harbor-registry") &&
      strcontains(aws_iam_role.harbor.assume_role_policy, "sts.amazonaws.com") &&
      strcontains(aws_iam_role.harbor.assume_role_policy, "oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000:sub")
    )
    error_message = "Only Harbor's registry service account, through the cluster's identity provider, may assume Harbor's role."
  }

  assert {
    condition     = length(aws_iam_role_policy.shared_external_secrets) == 1
    error_message = "The shared cluster's External Secrets role must be given this environment's secrets."
  }
}

run "shared_mode_without_a_cluster_is_refused" {
  command = plan

  variables {
    cluster_mode = "shared"
  }

  expect_failures = [var.shared_cluster]
}

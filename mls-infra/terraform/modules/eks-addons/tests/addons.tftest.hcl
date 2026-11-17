mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }
}

variables {
  name_prefix       = "mls-role-prod-lab"
  cluster_name      = "mls-eks-prod-lab-001"
  oidc_provider_arn = "arn:aws:iam::111111111111:oidc-provider/oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000"
  oidc_issuer       = "oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000"

  ebs_kms_key_arns              = ["arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"]
  external_secrets_secret_arns  = ["arn:aws:secretsmanager:ap-south-1:111111111111:secret:mls/prod/*"]
  external_secrets_kms_key_arns = ["arn:aws:kms:ap-south-1:111111111111:key/22222222-2222-2222-2222-222222222222"]
}

run "four_roles_by_default" {
  command = plan

  assert {
    condition     = length(aws_iam_role.this) == 4 && aws_iam_role.this["ebs-csi"].name == "mls-role-prod-lab-ebs-csi" && aws_iam_role.this["external-secrets"].name == "mls-role-prod-lab-external-secrets"
    error_message = "Four roles named <prefix>-<component>."
  }
}

run "each_role_trusts_only_its_own_service_account" {
  command = plan

  assert {
    condition = (
      jsondecode(aws_iam_role.this["load-balancer-controller"].assume_role_policy).Statement[0].Condition.StringEquals["oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000:sub"] == "system:serviceaccount:kube-system:aws-load-balancer-controller" &&
      jsondecode(aws_iam_role.this["external-secrets"].assume_role_policy).Statement[0].Condition.StringEquals["oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000:sub"] == "system:serviceaccount:external-secrets:external-secrets" &&
      jsondecode(aws_iam_role.this["ebs-csi"].assume_role_policy).Statement[0].Condition.StringEquals["oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000:aud"] == "sts.amazonaws.com"
    )
    error_message = "The trust policy must name the exact namespace and service account, and the sts audience."
  }
}

run "trust_uses_web_identity_from_the_cluster_provider" {
  command = plan

  assert {
    condition = (
      jsondecode(aws_iam_role.this["cluster-autoscaler"].assume_role_policy).Statement[0].Action == "sts:AssumeRoleWithWebIdentity" &&
      jsondecode(aws_iam_role.this["cluster-autoscaler"].assume_role_policy).Statement[0].Principal.Federated == "arn:aws:iam::111111111111:oidc-provider/oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000"
    )
    error_message = "Web identity trust with the cluster's own OIDC provider."
  }
}

run "service_account_names_can_be_changed" {
  command = plan

  variables {
    service_accounts = {
      cluster_autoscaler = { namespace = "autoscaling", name = "ca" }
    }
  }

  assert {
    condition     = jsondecode(aws_iam_role.this["cluster-autoscaler"].assume_role_policy).Statement[0].Condition.StringEquals["oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE0000000000000000000000000:sub"] == "system:serviceaccount:autoscaling:ca" && output.service_accounts["cluster-autoscaler"].name == "ca"
    error_message = "A changed service account must appear in the trust policy and in the output."
  }
}

run "autoscaler_can_only_scale_this_clusters_groups" {
  command = plan

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.inline["cluster-autoscaler"].policy).Statement[1].Condition.StringEquals["aws:ResourceTag/k8s.io/cluster-autoscaler/mls-eks-prod-lab-001"] == "owned" &&
      jsondecode(aws_iam_role_policy.inline["cluster-autoscaler"].policy).Statement[1].Condition.StringEquals["aws:ResourceTag/k8s.io/cluster-autoscaler/enabled"] == "true"
    )
    error_message = "Scaling and terminating are limited to groups tagged for this cluster."
  }
}

run "external_secrets_reads_only_the_listed_secrets" {
  command = plan

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.inline["external-secrets"].policy).Statement[0].Resource == ["arn:aws:secretsmanager:ap-south-1:111111111111:secret:mls/prod/*"] &&
      !contains(jsondecode(aws_iam_role_policy.inline["external-secrets"].policy).Statement[0].Action, "secretsmanager:PutSecretValue")
    )
    error_message = "Read access to the listed secrets only, and no write action."
  }
}

run "ebs_csi_gets_the_managed_policy_keys_and_the_addon" {
  command = plan

  assert {
    condition = (
      length(aws_iam_role_policy_attachment.ebs_csi) == 1 &&
      length(aws_eks_addon.ebs_csi) == 1 &&
      aws_eks_addon.ebs_csi[0].addon_name == "aws-ebs-csi-driver" &&
      jsondecode(aws_iam_role_policy.inline["ebs-csi"].policy).Statement[1].Condition.Bool["kms:GrantIsForAWSResource"] == "true"
    )
    error_message = "The EBS CSI role has the managed policy, a key-grant statement limited to AWS resources, and the add-on."
  }
}

run "load_balancer_controller_uses_the_official_policy_unchanged" {
  command = plan

  assert {
    condition     = length(jsondecode(aws_iam_role_policy.inline["load-balancer-controller"].policy).Statement) == 16
    error_message = "The official policy has 16 statements; it must not be edited."
  }
}

run "components_can_be_switched_off" {
  command = plan

  variables {
    enable_ebs_csi                  = false
    enable_load_balancer_controller = false
    enable_cluster_autoscaler       = false
    enable_external_secrets         = false
  }

  assert {
    condition     = length(aws_iam_role.this) == 0 && length(aws_eks_addon.ebs_csi) == 0 && length(aws_iam_role_policy.inline) == 0
    error_message = "Nothing is created when every component is off."
  }
}

run "rejects_ebs_csi_without_keys" {
  command = plan

  variables {
    ebs_kms_key_arns = []
  }

  expect_failures = [var.ebs_kms_key_arns]
}

run "rejects_external_secrets_with_a_bare_wildcard" {
  command = plan

  variables {
    external_secrets_secret_arns = ["*"]
  }

  expect_failures = [var.external_secrets_secret_arns]
}

run "rejects_external_secrets_without_secrets" {
  command = plan

  variables {
    external_secrets_secret_arns = []
  }

  expect_failures = [var.external_secrets_secret_arns]
}

run "rejects_an_issuer_with_a_scheme" {
  command = plan

  variables {
    oidc_issuer = "https://oidc.eks.ap-south-1.amazonaws.com/id/EXAMPLE"
  }

  expect_failures = [var.oidc_issuer]
}

data "aws_partition" "current" {}

locals {
  sa = {
    ebs-csi                  = coalesce(var.service_accounts.ebs_csi, {})
    load-balancer-controller = coalesce(var.service_accounts.load_balancer_controller, {})
    cluster-autoscaler       = coalesce(var.service_accounts.cluster_autoscaler, {})
    external-secrets         = coalesce(var.service_accounts.external_secrets, {})
  }

  enabled = {
    ebs-csi                  = var.enable_ebs_csi
    load-balancer-controller = var.enable_load_balancer_controller
    cluster-autoscaler       = var.enable_cluster_autoscaler
    external-secrets         = var.enable_external_secrets
  }

  components = {
    for k, on in local.enabled : k => {
      namespace = local.sa[k].namespace
      name      = local.sa[k].name
    } if on
  }

  # Inline policies. The EBS CSI driver also gets the AWS managed policy (below).
  inline_policies = merge(
    var.enable_ebs_csi ? {
      ebs-csi = jsonencode({
        Version = "2012-10-17"
        Statement = [
          {
            Sid      = "UseVolumeKeys"
            Effect   = "Allow"
            Action   = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
            Resource = var.ebs_kms_key_arns
          },
          {
            Sid       = "GrantsForVolumes"
            Effect    = "Allow"
            Action    = ["kms:CreateGrant", "kms:ListGrants", "kms:RevokeGrant"]
            Resource  = var.ebs_kms_key_arns
            Condition = { Bool = { "kms:GrantIsForAWSResource" = "true" } }
          },
        ]
      })
    } : {},
    var.enable_load_balancer_controller ? {
      # The controller's official policy, kept unchanged. See the README for its source and release.
      load-balancer-controller = file("${path.module}/policies/aws-load-balancer-controller.json")
    } : {},
    var.enable_cluster_autoscaler ? {
      cluster-autoscaler = jsonencode({
        Version = "2012-10-17"
        Statement = [
          {
            Sid    = "Describe"
            Effect = "Allow"
            Action = [
              "autoscaling:DescribeAutoScalingGroups",
              "autoscaling:DescribeAutoScalingInstances",
              "autoscaling:DescribeLaunchConfigurations",
              "autoscaling:DescribeScalingActivities",
              "autoscaling:DescribeTags",
              "ec2:DescribeImages",
              "ec2:DescribeInstanceTypes",
              "ec2:DescribeLaunchTemplateVersions",
              "ec2:GetInstanceTypesFromInstanceRequirements",
              "eks:DescribeNodegroup",
            ]
            Resource = "*"
          },
          {
            Sid      = "ScaleOnlyThisClustersGroups"
            Effect   = "Allow"
            Action   = ["autoscaling:SetDesiredCapacity", "autoscaling:TerminateInstanceInAutoScalingGroup"]
            Resource = "*"
            Condition = {
              StringEquals = {
                "aws:ResourceTag/k8s.io/cluster-autoscaler/enabled"             = "true"
                "aws:ResourceTag/k8s.io/cluster-autoscaler/${var.cluster_name}" = "owned"
              }
            }
          },
        ]
      })
    } : {},
    var.enable_external_secrets ? {
      external-secrets = jsonencode({
        Version = "2012-10-17"
        Statement = [
          {
            Sid      = "ReadListedSecrets"
            Effect   = "Allow"
            Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret", "secretsmanager:ListSecretVersionIds"]
            Resource = var.external_secrets_secret_arns
          },
          {
            Sid      = "DecryptThoseSecrets"
            Effect   = "Allow"
            Action   = ["kms:Decrypt", "kms:DescribeKey"]
            Resource = var.external_secrets_kms_key_arns
          },
        ]
      })
    } : {},
  )
}

resource "aws_iam_role" "this" {
  for_each = local.components

  name = "${var.name_prefix}-${each.key}"

  # Only this service account, in this namespace, on this cluster, may assume the role.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = var.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${var.oidc_issuer}:aud" = "sts.amazonaws.com"
          "${var.oidc_issuer}:sub" = "system:serviceaccount:${each.value.namespace}:${each.value.name}"
        }
      }
    }]
  })

  tags = merge(var.tags, { Name = "${var.name_prefix}-${each.key}", addon = each.key })
}

resource "aws_iam_role_policy" "inline" {
  for_each = local.inline_policies

  name   = each.key
  role   = aws_iam_role.this[each.key].id
  policy = each.value
}

resource "aws_iam_role_policy_attachment" "ebs_csi" {
  count = var.enable_ebs_csi ? 1 : 0

  role       = aws_iam_role.this["ebs-csi"].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

# The EBS CSI driver is an EKS managed add-on, installed here (not in the eks-cluster module)
# because its role needs the cluster's OIDC provider, which exists only after the cluster does.
resource "aws_eks_addon" "ebs_csi" {
  count = var.enable_ebs_csi ? 1 : 0

  cluster_name                = var.cluster_name
  addon_name                  = "aws-ebs-csi-driver"
  addon_version               = var.ebs_csi_addon_version
  service_account_role_arn    = aws_iam_role.this["ebs-csi"].arn
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  tags = merge(var.tags, { Name = "${var.cluster_name}-aws-ebs-csi-driver" })

  depends_on = [aws_iam_role_policy.inline, aws_iam_role_policy_attachment.ebs_csi]
}

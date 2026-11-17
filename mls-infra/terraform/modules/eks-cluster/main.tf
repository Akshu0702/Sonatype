data "aws_partition" "current" {}

locals {
  policy_prefix = "arn:${data.aws_partition.current.partition}:iam::aws:policy"

  node_policies = toset([
    "${local.policy_prefix}/AmazonEKSWorkerNodePolicy",
    "${local.policy_prefix}/AmazonEKS_CNI_Policy",
    "${local.policy_prefix}/AmazonEC2ContainerRegistryReadOnly",
    "${local.policy_prefix}/AmazonSSMManagedInstanceCore",
  ])

  # ------------------------------------------------------------ node hardening (user data)
  hardening = var.node_hardening

  # Kernel settings the kubelet expects when protectKernelDefaults is on, plus the caller's own.
  kubelet_sysctls = {
    "vm.overcommit_memory"        = "1"
    "vm.panic_on_oom"             = "0"
    "kernel.panic"                = "10"
    "kernel.panic_on_oops"        = "1"
    "kernel.keys.root_maxkeys"    = "1000000"
    "kernel.keys.root_maxbytes"   = "25000000"
    "net.ipv4.conf.all.rp_filter" = "1"
  }
  node_sysctls = merge(local.kubelet_sysctls, local.hardening.extra_sysctls)

  # Merged by EKS into the node's own NodeConfig (the cluster endpoint and certificate are added by EKS).
  node_config = {
    apiVersion = "node.eks.aws/v1alpha1"
    kind       = "NodeConfig"
    spec = {
      kubelet = {
        config = merge(
          {
            readOnlyPort = 0
            authentication = {
              anonymous = { enabled = false }
              webhook   = { enabled = true }
            }
            authorization                  = { mode = "Webhook" }
            streamingConnectionIdleTimeout = "5m"
            tlsMinVersion                  = "VersionTLS12"
            rotateCertificates             = true
            seccompDefault                 = local.hardening.seccomp_default
            protectKernelDefaults          = local.hardening.protect_kernel_defaults
            serverTLSBootstrap             = local.hardening.server_tls_bootstrap
          },
          local.hardening.pod_pids_limit > 0 ? { podPidsLimit = local.hardening.pod_pids_limit } : {}
        )
      }
    }
  }

  node_script = join("\n", concat(
    [
      "#!/bin/bash",
      "set -euo pipefail",
      "# Kernel settings",
      "cat > /etc/sysctl.d/90-mls-node.conf <<'SYSCTL'",
    ],
    [for k in sort(keys(local.node_sysctls)) : "${k} = ${local.node_sysctls[k]}"],
    [
      "SYSCTL",
      "sysctl --system",
      "# Kernel modules that a Kubernetes node does not need",
    ],
    [for mod in local.hardening.disabled_kernel_modules : "echo 'install ${mod} /bin/true' > /etc/modprobe.d/90-mls-disable-${mod}.conf"],
    [""],
  ))

  node_user_data = local.hardening.enabled ? join("\n", [
    "MIME-Version: 1.0",
    "Content-Type: multipart/mixed; boundary=\"MLSBOUNDARY\"",
    "",
    "--MLSBOUNDARY",
    "Content-Type: application/node.eks.aws",
    "",
    jsonencode(local.node_config),
    "",
    "--MLSBOUNDARY",
    "Content-Type: text/x-shellscript; charset=\"us-ascii\"",
    "",
    local.node_script,
    "--MLSBOUNDARY--",
    "",
  ]) : null
}

# ---------------------------------------------------------------- control plane
resource "aws_iam_role" "cluster" {
  name = "${var.name}-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, { Name = "${var.name}-cluster" })
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "${local.policy_prefix}/AmazonEKSClusterPolicy"
}

resource "aws_eks_cluster" "this" {
  name     = var.name
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  # The API endpoint is private only. It is reached over the VPN and the Transit Gateway.
  vpc_config {
    subnet_ids              = var.subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = false
    security_group_ids      = var.additional_security_group_ids
  }

  # Kubernetes secrets are encrypted with our key.
  encryption_config {
    provider {
      key_arn = var.kms_key_arn
    }
    resources = ["secrets"]
  }

  # Access entries only. The creator is not an administrator, so access is always an explicit, reviewed entry.
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = false
  }

  kubernetes_network_config {
    service_ipv4_cidr = var.service_ipv4_cidr
    ip_family         = "ipv4"
  }

  enabled_cluster_log_types = var.enabled_log_types

  tags = merge(var.tags, { Name = var.name })

  depends_on = [aws_iam_role_policy_attachment.cluster]
}

resource "aws_eks_access_entry" "this" {
  for_each = var.access_entries

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value.principal_arn
  type          = "STANDARD"

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}

resource "aws_eks_access_policy_association" "this" {
  for_each = var.access_entries

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value.principal_arn
  policy_arn    = each.value.policy_arn

  access_scope {
    type       = each.value.access_scope_type
    namespaces = each.value.access_scope_type == "namespace" ? each.value.namespaces : null
  }

  depends_on = [aws_eks_access_entry.this]
}

# IAM roles for service accounts (IRSA).
resource "aws_iam_openid_connect_provider" "this" {
  url            = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list = ["sts.amazonaws.com"]

  tags = merge(var.tags, { Name = "${var.name}-oidc" })
}

# ---------------------------------------------------------------- nodes
resource "aws_iam_role" "node" {
  name = "${var.name}-node"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, { Name = "${var.name}-node" })
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = local.node_policies

  role       = aws_iam_role.node.name
  policy_arn = each.value
}

resource "aws_launch_template" "node" {
  for_each = var.node_groups

  name_prefix            = "${var.name}-${each.key}-"
  update_default_version = true
  user_data              = local.node_user_data == null ? null : base64encode(local.node_user_data)

  # No key pair is set and the node group has no remote_access block: there is no SSH. Sessions go through SSM.

  # IMDSv2 only, one hop: pods cannot reach the node's credentials through the metadata service.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  monitoring {
    enabled = true
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = each.value.disk_size_gb
      volume_type           = "gp3"
      encrypted             = true
      kms_key_id            = var.kms_key_arn
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags          = merge(var.tags, { Name = "${var.name}-${each.key}" })
  }

  tag_specifications {
    resource_type = "volume"
    tags          = merge(var.tags, { Name = "${var.name}-${each.key}" })
  }

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}

resource "aws_eks_node_group" "this" {
  for_each = var.node_groups

  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.name}-${each.key}"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = each.value.subnet_ids
  instance_types  = each.value.instance_types
  ami_type        = each.value.ami_type
  capacity_type   = each.value.capacity_type
  labels          = each.value.labels

  scaling_config {
    min_size     = each.value.min_size
    desired_size = each.value.desired_size
    max_size     = each.value.max_size
  }

  update_config {
    max_unavailable = 1
  }

  launch_template {
    id      = aws_launch_template.node[each.key].id
    version = aws_launch_template.node[each.key].latest_version
  }

  dynamic "taint" {
    for_each = each.value.taints

    content {
      key    = taint.value.key
      value  = taint.value.value
      effect = taint.value.effect
    }
  }

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })

  # The cluster autoscaler changes the desired size; do not fight it.
  lifecycle {
    ignore_changes = [scaling_config[0].desired_size]
  }

  depends_on = [aws_iam_role_policy_attachment.node]
}

# ---------------------------------------------------------------- add-ons
resource "aws_eks_addon" "this" {
  for_each = var.cluster_addons

  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.key
  addon_version               = each.value.version
  service_account_role_arn    = each.value.service_account_role_arn
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = each.value.resolve_conflicts

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })

  depends_on = [aws_eks_node_group.this]
}

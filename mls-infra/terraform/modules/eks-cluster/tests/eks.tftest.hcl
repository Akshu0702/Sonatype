mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }
}

# One Artifactory node per availability zone (zonal volumes) and two system nodes, as in the Gate 1 design.
variables {
  name               = "mls-eks-prod-lab-001"
  kubernetes_version = "1.31"
  subnet_ids         = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2", "subnet-0aaaaaaaaaaaaaaa3"]
  kms_key_arn        = "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"

  access_entries = {
    platform_admin = { principal_arn = "arn:aws:iam::111111111111:role/mls-platform-admin" }
  }

  node_groups = {
    artifactory-a = {
      subnet_ids     = ["subnet-0aaaaaaaaaaaaaaa1"]
      instance_types = ["m6i.2xlarge"]
      min_size       = 1
      desired_size   = 1
      max_size       = 2
      disk_size_gb   = 200
      labels         = { workload = "artifactory" }
    }
    artifactory-b = {
      subnet_ids     = ["subnet-0aaaaaaaaaaaaaaa2"]
      instance_types = ["m6i.2xlarge"]
      min_size       = 1
      desired_size   = 1
      max_size       = 2
      disk_size_gb   = 200
      labels         = { workload = "artifactory" }
    }
    artifactory-c = {
      subnet_ids     = ["subnet-0aaaaaaaaaaaaaaa3"]
      instance_types = ["m6i.2xlarge"]
      min_size       = 1
      desired_size   = 1
      max_size       = 2
      disk_size_gb   = 200
      labels         = { workload = "artifactory" }
    }
    system = {
      subnet_ids     = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2"]
      instance_types = ["m6i.large"]
      min_size       = 2
      desired_size   = 2
      max_size       = 3
      labels         = { workload = "system" }
    }
  }
}

run "api_endpoint_is_private_only" {
  command = plan

  assert {
    condition     = aws_eks_cluster.this.vpc_config[0].endpoint_private_access == true && aws_eks_cluster.this.vpc_config[0].endpoint_public_access == false
    error_message = "The API endpoint must be private only."
  }
}

run "secrets_are_encrypted_with_our_key" {
  command = plan

  assert {
    condition     = aws_eks_cluster.this.encryption_config[0].resources == toset(["secrets"]) && aws_eks_cluster.this.encryption_config[0].provider[0].key_arn == "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
    error_message = "Secrets must be encrypted with the customer-managed key."
  }
}

run "all_control_plane_logs_are_on" {
  command = plan

  assert {
    condition     = aws_eks_cluster.this.enabled_cluster_log_types == toset(["api", "audit", "authenticator", "controllerManager", "scheduler"])
    error_message = "All five log types are expected."
  }
}

run "access_is_by_explicit_entries_only" {
  command = plan

  assert {
    condition     = aws_eks_cluster.this.access_config[0].authentication_mode == "API" && aws_eks_cluster.this.access_config[0].bootstrap_cluster_creator_admin_permissions == false
    error_message = "Access entries only, and the creator is not an administrator."
  }

  assert {
    condition     = length(aws_eks_access_entry.this) == 1 && length(aws_eks_access_policy_association.this) == 1
    error_message = "One access entry and one policy association."
  }
}

run "node_groups_match_the_design" {
  command = plan

  assert {
    condition     = length(aws_eks_node_group.this) == 4 && length(aws_launch_template.node) == 4
    error_message = "Three per-zone Artifactory groups and one system group."
  }

  assert {
    condition     = aws_eks_node_group.this["artifactory-a"].instance_types[0] == "m6i.2xlarge" && aws_eks_node_group.this["system"].instance_types[0] == "m6i.large"
    error_message = "Instance types follow the BOM."
  }
}

run "nodes_use_imdsv2_and_encrypted_volumes" {
  command = plan

  assert {
    condition     = aws_launch_template.node["system"].metadata_options[0].http_tokens == "required" && aws_launch_template.node["system"].metadata_options[0].http_put_response_hop_limit == 1
    error_message = "IMDSv2 only with a hop limit of 1."
  }

  assert {
    condition     = aws_launch_template.node["artifactory-a"].block_device_mappings[0].ebs[0].encrypted == "true" && aws_launch_template.node["artifactory-a"].block_device_mappings[0].ebs[0].volume_type == "gp3" && aws_launch_template.node["artifactory-a"].block_device_mappings[0].ebs[0].volume_size == 200
    error_message = "Root volumes are encrypted gp3 of the requested size."
  }
}

run "core_addons_are_on_by_default" {
  command = plan

  assert {
    condition     = length(aws_eks_addon.this) == 3 && contains(keys(aws_eks_addon.this), "vpc-cni") && contains(keys(aws_eks_addon.this), "coredns") && contains(keys(aws_eks_addon.this), "kube-proxy")
    error_message = "vpc-cni, coredns and kube-proxy by default."
  }
}

run "irsa_provider_and_ssm_access_for_nodes" {
  command = plan

  assert {
    condition     = aws_iam_openid_connect_provider.this.client_id_list == toset(["sts.amazonaws.com"]) && length(aws_iam_role_policy_attachment.node) == 4
    error_message = "An OIDC provider for service accounts and four managed policies on the node role."
  }
}

run "rejects_a_cluster_nobody_can_administer" {
  command = plan

  variables {
    access_entries = {
      reader = { principal_arn = "arn:aws:iam::111111111111:role/mls-reader", policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSViewPolicy" }
    }
  }

  expect_failures = [var.access_entries]
}

run "rejects_a_bad_version" {
  command = plan

  variables {
    kubernetes_version = "1.3"
  }

  expect_failures = [var.kubernetes_version]
}

run "rejects_min_greater_than_desired" {
  command = plan

  variables {
    node_groups = {
      bad = { subnet_ids = ["subnet-0aaaaaaaaaaaaaaa1"], instance_types = ["m6i.large"], min_size = 3, desired_size = 1, max_size = 3 }
    }
  }

  expect_failures = [var.node_groups]
}

run "rejects_a_single_subnet_control_plane" {
  command = plan

  variables {
    subnet_ids = ["subnet-0aaaaaaaaaaaaaaa1"]
  }

  expect_failures = [var.subnet_ids]
}

run "node_hardening_is_on_by_default" {
  command = plan

  assert {
    condition     = aws_launch_template.node["system"].user_data != null && aws_launch_template.node["artifactory-a"].user_data == aws_launch_template.node["system"].user_data
    error_message = "Every node group gets the hardening user data."
  }

  assert {
    condition     = strcontains(local.node_user_data, "\"readOnlyPort\":0") && strcontains(local.node_user_data, "\"anonymous\":{\"enabled\":false}") && strcontains(local.node_user_data, "\"authorization\":{\"mode\":\"Webhook\"}")
    error_message = "The kubelet must have the read-only port off, no anonymous access and webhook authorization."
  }

  assert {
    condition     = strcontains(local.node_user_data, "\"tlsMinVersion\":\"VersionTLS12\"") && strcontains(local.node_user_data, "\"seccompDefault\":true") && strcontains(local.node_user_data, "\"podPidsLimit\":4096") && strcontains(local.node_user_data, "\"streamingConnectionIdleTimeout\":\"5m\"")
    error_message = "TLS 1.2 minimum, default seccomp profile, a pod process limit and a streaming idle timeout are expected."
  }
}

run "risky_kubelet_options_are_off_by_default" {
  command = plan

  assert {
    condition     = strcontains(local.node_user_data, "\"protectKernelDefaults\":false") && strcontains(local.node_user_data, "\"serverTLSBootstrap\":false")
    error_message = "protectKernelDefaults and serverTLSBootstrap need things outside this module, so they are off until chosen."
  }
}

run "user_data_is_a_valid_multipart_with_kernel_settings" {
  command = plan

  assert {
    condition     = startswith(local.node_user_data, "MIME-Version: 1.0") && strcontains(local.node_user_data, "Content-Type: application/node.eks.aws") && strcontains(local.node_user_data, "Content-Type: text/x-shellscript") && endswith(trimspace(local.node_user_data), "--MLSBOUNDARY--")
    error_message = "The user data must be a closed MIME multipart with a NodeConfig part and a shell part."
  }

  assert {
    condition     = strcontains(local.node_user_data, "\n--MLSBOUNDARY\nContent-Type: text/x-shellscript; charset=\"us-ascii\"\n\n#!/bin/bash\nset -euo pipefail\n") && length(split("\n", local.node_user_data)) > 20
    error_message = "Parts must be separated by real line breaks, not literal backslash sequences."
  }

  assert {
    condition     = strcontains(local.node_user_data, "kernel.panic_on_oops = 1") && strcontains(local.node_user_data, "vm.overcommit_memory = 1") && strcontains(local.node_user_data, "install sctp /bin/true") && strcontains(local.node_user_data, "install usb-storage /bin/true")
    error_message = "Kernel settings and disabled modules must be written."
  }
}

run "nodes_have_no_ssh_and_no_instance_tags_in_metadata" {
  command = plan

  assert {
    condition     = alltrue([for k, lt in aws_launch_template.node : lt.key_name == null || lt.key_name == ""]) && alltrue([for k, g in aws_eks_node_group.this : length(g.remote_access) == 0])
    error_message = "No key pair and no remote access block: access is through Session Manager only."
  }

  assert {
    condition     = aws_launch_template.node["system"].metadata_options[0].instance_metadata_tags == "disabled" && aws_launch_template.node["system"].monitoring[0].enabled == true
    error_message = "Instance tags are not exposed through metadata, and detailed monitoring is on."
  }
}

run "hardening_options_are_applied_when_chosen" {
  command = plan

  variables {
    node_hardening = {
      protect_kernel_defaults = true
      pod_pids_limit          = 0
      disabled_kernel_modules = ["cramfs"]
      extra_sysctls           = { "fs.inotify.max_user_instances" = "8192" }
    }
  }

  assert {
    condition     = strcontains(local.node_user_data, "\"protectKernelDefaults\":true") && !strcontains(local.node_user_data, "podPidsLimit")
    error_message = "protect_kernel_defaults on and a zero pids limit (omitted) are expected."
  }

  assert {
    condition     = strcontains(local.node_user_data, "fs.inotify.max_user_instances = 8192") && strcontains(local.node_user_data, "install cramfs /bin/true") && !strcontains(local.node_user_data, "install sctp")
    error_message = "Extra kernel settings are added and the module list is replaced."
  }
}

run "hardening_can_be_switched_off_explicitly" {
  command = plan

  variables {
    node_hardening = { enabled = false }
  }

  assert {
    condition     = alltrue([for k, lt in aws_launch_template.node : lt.user_data == null])
    error_message = "With hardening disabled there is no user data."
  }
}

run "other_operating_systems_are_rejected" {
  command = plan

  variables {
    node_groups = {
      system = {
        subnet_ids     = ["subnet-0aaaaaaaaaaaaaaa1"]
        instance_types = ["m6i.large"]
        min_size       = 1
        desired_size   = 1
        max_size       = 2
        ami_type       = "BOTTLEROCKET_x86_64"
      }
    }
  }

  expect_failures = [var.node_groups]
}

run "unsafe_module_names_and_sysctls_are_rejected" {
  command = plan

  variables {
    node_hardening = {
      disabled_kernel_modules = ["sctp; rm -rf /"]
      extra_sysctls           = { "vm.max_map_count" = "abc" }
    }
  }

  expect_failures = [var.node_hardening]
}

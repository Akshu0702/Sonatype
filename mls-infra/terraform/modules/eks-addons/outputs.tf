output "role_arns" {
  description = "IAM role ARNs by add-on (ebs-csi, load-balancer-controller, cluster-autoscaler, external-secrets). Put them in the Helm values as the service account's eks.amazonaws.com/role-arn annotation."
  value       = { for k, r in aws_iam_role.this : k => r.arn }
}

output "service_accounts" {
  description = "Namespace and name of each add-on's service account, for the Helm values."
  value       = local.components
}

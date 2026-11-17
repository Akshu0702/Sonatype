output "cluster_name" {
  description = "EKS cluster the environment runs on."
  value       = local.cluster_name
}

output "vpc_id" {
  description = "VPC of the environment."
  value       = local.vpc_id
}

output "addon_role_arns" {
  description = "IAM roles of the cluster add-ons (dedicated mode), for the Helm values."
  value       = local.dedicated ? one(module.addons[*].role_arns) : {}
}

output "harbor_role_arn" {
  description = "IAM role of Harbor's registry service account: the eks.amazonaws.com/role-arn annotation in the Harbor values."
  value       = aws_iam_role.harbor.arn
}

output "registry_bucket" {
  description = "S3 bucket that holds the registry's image data."
  value       = module.registry_bucket.bucket_id
}

output "database_address" {
  description = "PostgreSQL endpoint. Reachable only from inside the VPC."
  value       = module.rds.address
}

output "database_master_secret_arn" {
  description = "Secret that holds the master user's password (created and rotated by RDS). Used once, to create the application users; never by a service."
  value       = module.rds.master_user_secret_arn
}

output "secret_names" {
  description = "Names of the empty secrets to fill, by key."
  value       = module.secrets.secret_names
}

output "kms_key_arns" {
  description = "Keys by purpose."
  value       = module.kms.key_arns
}

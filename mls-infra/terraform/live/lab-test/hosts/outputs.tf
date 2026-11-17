output "agent" {
  description = "The build agent."
  value = {
    instance_id = module.agent.instance_id
    private_ip  = module.agent.private_ip
    role_arn    = module.agent.role_arn
  }
}

output "app_host" {
  description = "The application host."
  value = {
    instance_id = module.app.instance_id
    private_ip  = module.app.private_ip
    role_arn    = module.app.role_arn
  }
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "open_session_agent" {
  description = "Command to open a shell on the build agent (needs the Session Manager plugin)."
  value       = "aws ssm start-session --target ${module.agent.instance_id} --region ${var.region} --profile mls-deploy"
}

output "open_session_app" {
  description = "Command to open a shell on the application host."
  value       = "aws ssm start-session --target ${module.app.instance_id} --region ${var.region} --profile mls-deploy"
}

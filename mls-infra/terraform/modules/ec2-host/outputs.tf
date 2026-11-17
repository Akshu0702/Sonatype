output "instance_id" {
  description = "Instance id."
  value       = aws_instance.this.id
}

output "instance_arn" {
  description = "Instance ARN."
  value       = aws_instance.this.arn
}

output "private_ip" {
  description = "Private address."
  value       = aws_instance.this.private_ip
}

output "public_ip" {
  description = "Public address, or null. It changes whenever the instance is stopped and started."
  value       = aws_instance.this.public_ip
}

output "role_name" {
  description = "Name of the instance role."
  value       = aws_iam_role.this.name
}

output "role_arn" {
  description = "ARN of the instance role."
  value       = aws_iam_role.this.arn
}

output "security_group_id" {
  description = "Security group of the instance, for other groups to refer to."
  value       = aws_security_group.this.id
}

output "ami_id" {
  description = "Image the instance was launched from."
  value       = local.ami_id
}

output "schedule_names" {
  description = "Names of the start and stop schedules, empty when no timetable is set."
  value       = concat(aws_scheduler_schedule.start[*].name, aws_scheduler_schedule.stop[*].name)
}

output "pipeline_arn" {
  description = "The pipeline that builds the hardened image."
  value       = aws_imagebuilder_image_pipeline.this.arn
}

output "pipeline_name" {
  description = "Name of the pipeline."
  value       = aws_imagebuilder_image_pipeline.this.name
}

output "ami_name_prefix" {
  description = "Pass this to the provisioning module as hardened_ami_name_prefix: it uses the newest image with this prefix."
  value       = var.ami_name_prefix
}

output "component_arn" {
  description = "The hardening component."
  value       = aws_imagebuilder_component.harden.arn
}

output "start_build_command" {
  description = "How to start a build by hand (needs imagebuilder:StartImagePipelineExecution)."
  value       = "aws imagebuilder start-image-pipeline-execution --region ${var.region} --image-pipeline-arn ${aws_imagebuilder_image_pipeline.this.arn}"
}

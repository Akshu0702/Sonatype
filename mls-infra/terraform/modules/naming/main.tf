locals {
  name = join("-", [var.project, var.resource_type, var.environment, var.function, var.id])

  tags = merge(
    var.extra_tags,
    {
      project      = var.project
      environment  = var.environment
      function     = var.function
      owner        = var.owner
      "managed-by" = var.managed_by
      "data-class" = var.data_class
    },
    var.region == null ? {} : { region = var.region },
    var.name_long == null ? {} : { "name-long" = var.name_long },
  )
}

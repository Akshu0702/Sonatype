terraform {
  # templatestring (fills a template held in a variable) and validations that read other variables need 1.9.
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

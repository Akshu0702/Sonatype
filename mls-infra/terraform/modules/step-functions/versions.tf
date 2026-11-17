terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # State machine versions and aliases need 5.60 or newer.
      version = "~> 5.60"
    }
  }
}

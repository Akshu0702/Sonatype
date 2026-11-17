terraform {
  # Constraints are ranges for now. Exact Terraform and provider versions are
  # pinned when the toolchain is chosen (wiki: Known Issues and Gaps, item 17).
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

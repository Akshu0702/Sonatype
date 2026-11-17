mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  # Real-looking identifiers, because the mock would otherwise invent strings that are not valid ARNs.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::111111111111:role/mock-role"
    }
  }

  mock_resource "aws_imagebuilder_component" {
    defaults = {
      arn = "arn:aws:imagebuilder:ap-south-1:111111111111:component/mls-img-dev-lab-001-harden/1.0.0/1"
    }
  }

  mock_resource "aws_imagebuilder_image_pipeline" {
    defaults = {
      arn = "arn:aws:imagebuilder:ap-south-1:111111111111:image-pipeline/mls-img-dev-lab-001-pipeline"
    }
  }
}

variables {
  account_id            = "111111111111"
  vpc_id                = "vpc-0aaaaaaaaaaaaaaa1"
  subnet_id             = "subnet-0aaaaaaaaaaaaaaa1"
  artifact_bucket       = "mls-deploy-artifacts-111111111111"
  hardening_archive_key = "images/ansible-1.0.0.tar.gz"
  tags                  = { project = "mls", environment = "dev" }
}

# Everything below runs against a mocked provider: nothing is built or created anywhere.

run "the_image_is_encrypted_gp3_and_keeps_the_session_manager_agent" {
  command = plan

  assert {
    condition     = one(aws_imagebuilder_image_recipe.this.block_device_mapping).ebs[0].encrypted == "true" && one(aws_imagebuilder_image_recipe.this.block_device_mapping).ebs[0].volume_type == "gp3" && one(aws_imagebuilder_image_recipe.this.block_device_mapping).ebs[0].volume_size == 30
    error_message = "Encrypted gp3 root volume."
  }

  assert {
    condition     = aws_imagebuilder_image_recipe.this.systems_manager_agent[0].uninstall_after_build == false
    error_message = "Session Manager is the only way in, so the agent stays."
  }

  assert {
    condition     = endswith(aws_imagebuilder_image_recipe.this.parent_image, "image/ubuntu-server-24-lts-x86/x.x.x")
    error_message = "Starts from the AWS-managed Ubuntu 24.04 LTS image."
  }
}

run "the_component_applies_the_hardening_playbook_from_the_named_archive" {
  command = plan

  assert {
    condition     = aws_imagebuilder_component.harden.platform == "Linux" && aws_imagebuilder_component.harden.version == "1.0.0"
    error_message = "A Linux component with a version."
  }

  assert {
    condition     = strcontains(aws_imagebuilder_component.harden.data, "s3://mls-deploy-artifacts-111111111111/images/ansible-1.0.0.tar.gz") && strcontains(aws_imagebuilder_component.harden.data, "playbooks/harden.yml") && strcontains(aws_imagebuilder_component.harden.data, "expectedBucketOwner: \"111111111111\"")
    error_message = "It downloads the archive from our bucket (owner checked) and runs harden.yml."
  }

  assert {
    condition     = !strcontains(aws_imagebuilder_component.harden.data, "$${") && strcontains(aws_imagebuilder_component.harden.data, "schemaVersion: 1.0")
    error_message = "No placeholder is left in the component document."
  }

  assert {
    condition     = strcontains(aws_imagebuilder_component.harden.data, "name: validate") && strcontains(aws_imagebuilder_component.harden.data, "name: test")
    error_message = "The build is validated and tested, not just run."
  }
}

run "the_build_instance_is_small_locked_down_and_removed_even_when_the_build_fails" {
  command = plan

  assert {
    condition     = aws_imagebuilder_infrastructure_configuration.this.terminate_instance_on_failure == true && aws_imagebuilder_infrastructure_configuration.this.instance_types == toset(["t3.medium"])
    error_message = "One t3.medium, terminated on failure."
  }

  assert {
    condition     = aws_imagebuilder_infrastructure_configuration.this.instance_metadata_options[0].http_tokens == "required" && aws_imagebuilder_infrastructure_configuration.this.instance_metadata_options[0].http_put_response_hop_limit == 1
    error_message = "IMDSv2 only, one hop."
  }

  assert {
    condition     = length(aws_vpc_security_group_egress_rule.build) == 2
    error_message = "Web traffic out, nothing in."
  }
}

run "the_build_role_reads_the_archive_prefix_and_nothing_else_in_the_bucket" {
  command = plan

  assert {
    condition     = endswith(aws_iam_role_policy_attachment.build_ssm.policy_arn, "/AmazonSSMManagedInstanceCore") && endswith(aws_iam_role_policy_attachment.build_imagebuilder.policy_arn, "/EC2InstanceProfileForImageBuilder")
    error_message = "The two policies Image Builder documents for its build instance."
  }

  assert {
    condition     = jsondecode(aws_iam_role_policy.build_archive.policy).Statement[0].Resource == "arn:aws:s3:::mls-deploy-artifacts-111111111111/images/*" && jsondecode(aws_iam_role_policy.build_archive.policy).Statement[0].Action == "s3:GetObject"
    error_message = "Read-only, and only under images/."
  }
}

run "images_stay_in_this_account_and_region_and_are_named_for_lookup" {
  command = plan

  assert {
    condition     = one(aws_imagebuilder_distribution_configuration.this.distribution).region == "ap-south-1" && startswith(one(aws_imagebuilder_distribution_configuration.this.distribution).ami_distribution_configuration[0].name, "mls-hardened-ubuntu-24-")
    error_message = "Mumbai only, and a name the provisioning module can search by."
  }

  assert {
    condition     = try(length(one(aws_imagebuilder_distribution_configuration.this.distribution).ami_distribution_configuration[0].launch_permission), 0) == 0 && try(length(one(aws_imagebuilder_distribution_configuration.this.distribution).ami_distribution_configuration[0].target_account_ids), 0) == 0
    error_message = "The image is never shared with another account."
  }
}

run "every_new_image_is_scanned_and_tested_by_default_and_nothing_runs_by_itself" {
  command = plan

  assert {
    condition     = aws_imagebuilder_image_pipeline.this.image_scanning_configuration[0].image_scanning_enabled == true && aws_imagebuilder_image_pipeline.this.image_tests_configuration[0].image_tests_enabled == true
    error_message = "Scanned and tested."
  }

  assert {
    condition     = length(aws_imagebuilder_image_pipeline.this.schedule) == 0
    error_message = "No timetable unless asked for: a build starts when someone starts it."
  }
}

run "a_timetable_can_be_set" {
  command = plan

  variables {
    schedule_expression = "cron(0 3 1 * ? *)"
  }

  assert {
    condition     = length(aws_imagebuilder_image_pipeline.this.schedule) == 1 && aws_imagebuilder_image_pipeline.this.schedule[0].schedule_expression == "cron(0 3 1 * ? *)"
    error_message = "The timetable is applied."
  }
}

run "scanning_can_be_switched_off_where_inspector_is_not_enabled_yet" {
  command = plan

  variables {
    scan_images = false
  }

  assert {
    condition     = aws_imagebuilder_image_pipeline.this.image_scanning_configuration[0].image_scanning_enabled == false
    error_message = "Scanning off means off."
  }
}

run "names_follow_the_mls_standard" {
  command = plan

  assert {
    condition     = aws_imagebuilder_image_pipeline.this.name == "mls-img-dev-lab-001-pipeline" && aws_iam_role.build.name == "mls-img-dev-lab-001-build" && aws_imagebuilder_image_recipe.this.name == "mls-img-dev-lab-001-recipe"
    error_message = "Every name starts with mls-, as the deploy policy requires."
  }
}

# ---- what must be refused

run "a_bucket_that_is_not_an_mls_bucket_is_refused" {
  command = plan

  variables {
    artifact_bucket = "somebody-elses-bucket"
  }

  expect_failures = [var.artifact_bucket]
}

run "an_archive_outside_images_is_refused" {
  command = plan

  variables {
    hardening_archive_key = "deploy/ansible.tar.gz"
  }

  expect_failures = [var.hardening_archive_key]
}

run "another_region_is_refused" {
  command = plan

  variables {
    region = "us-east-1"
  }

  expect_failures = [var.region]
}

run "a_version_that_is_not_x_y_z_is_refused" {
  command = plan

  variables {
    component_version = "1.0"
  }

  expect_failures = [var.component_version]
}

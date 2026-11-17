mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  mock_data "aws_ami" {
    defaults = {
      id = "ami-0aaaaaaaaaaaaaaa1"
    }
  }

  # Real-looking identifiers, because the mock would otherwise invent strings that are not valid ARNs.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::111111111111:role/mock-role"
    }
  }

  mock_resource "aws_security_group" {
    defaults = {
      id  = "sg-0123456789abcdef0"
      arn = "arn:aws:ec2:ap-south-1:111111111111:security-group/sg-0123456789abcdef0"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:ap-south-1:111111111111:log-group:/aws/vendedlogs/states/mock"
    }
  }

  mock_resource "aws_sfn_state_machine" {
    defaults = {
      arn                       = "arn:aws:states:ap-south-1:111111111111:stateMachine:mock"
      state_machine_version_arn = "arn:aws:states:ap-south-1:111111111111:stateMachine:mock:1"
    }
  }

  mock_resource "aws_sfn_alias" {
    defaults = {
      arn = "arn:aws:states:ap-south-1:111111111111:stateMachine:mock:dev"
    }
  }
}

variables {
  account_id           = "111111111111"
  vpc_id               = "vpc-0aaaaaaaaaaaaaaa1"
  subnet_ids           = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0bbbbbbbbbbbbbbb2"]
  provision_definition = file("tests/fixtures/provision.asl.json")
  reaper_definition    = file("tests/fixtures/reaper.asl.json")
  tags                 = { project = "mls", environment = "dev" }

  configurations = {
    "ubuntu24-pg16" = {
      description      = "Ubuntu 24.04 with PostgreSQL 16"
      postgres_package = "postgresql-16"
    }
    "ubuntu24-plain" = {
      instance_type = "t3.medium"
    }
  }
}

# Everything below runs against a mocked provider: nothing is created anywhere.

run "every_placeholder_in_a_template_is_filled_in" {
  command = apply

  assert {
    condition     = jsondecode(output.rendered_definitions.provision).States.A.Assign.sg == "sg-0123456789abcdef0" && jsondecode(output.rendered_definitions.provision).States.A.Assign.profile == "mls-iprof-dev-lab-001"
    error_message = "The security group and the instance profile are filled in."
  }

  assert {
    condition     = jsondecode(output.rendered_definitions.provision).States.A.Assign.env == "dev" && jsondecode(output.rendered_definitions.provision).States.A.Assign.publicIp == "{% false %}" && jsondecode(output.rendered_definitions.provision).States.A.Assign.defaultTtl == "{% 8 %}" && jsondecode(output.rendered_definitions.provision).States.A.Assign.maxTtl == "{% 72 %}"
    error_message = "Environment, public address, default and maximum life are filled in."
  }

  assert {
    condition     = !strcontains(output.rendered_definitions.provision, "$${") && !strcontains(output.rendered_definitions.reaper, "$${")
    error_message = "No placeholder is left over."
  }
}

run "the_catalogue_and_subnets_reach_the_state_machine_as_json" {
  command = apply

  assert {
    condition     = strcontains(jsondecode(output.rendered_definitions.provision).States.A.Assign.cfg, "\"amiId\":\"ami-0aaaaaaaaaaaaaaa1\"") && strcontains(jsondecode(output.rendered_definitions.provision).States.A.Assign.cfg, "\"instanceType\":\"t3.small\"") && strcontains(jsondecode(output.rendered_definitions.provision).States.A.Assign.cfg, "\"rootVolumeGb\":30")
    error_message = "A configuration resolves to an image, a size and a disk."
  }

  assert {
    condition     = jsondecode(output.rendered_definitions.provision).States.A.Assign.subnets == "{% [\"subnet-0aaaaaaaaaaaaaaa1\",\"subnet-0bbbbbbbbbbbbbbb2\"] %}"
    error_message = "The subnets are the ones given."
  }
}

run "postgres_is_installed_only_where_a_configuration_asks_for_it" {
  command = apply

  assert {
    condition     = strcontains(jsondecode(output.rendered_definitions.provision).States.A.Assign.cfg, "apt-get install -y --no-install-recommends postgresql-16")
    error_message = "The PostgreSQL configuration installs its package at first boot."
  }

  assert {
    condition     = length(regexall("postgresql-16", output.rendered_definitions.provision)) == 1
    error_message = "The plain configuration installs no database."
  }
}

run "without_an_image_the_ubuntu_fallback_is_used_and_said_so" {
  command = apply

  assert {
    condition     = output.image_source == "ubuntu-fallback" && output.uses_fallback_image == true
    error_message = "The fallback must be visible: it is not hardened."
  }
}

run "a_given_image_is_used_as_given" {
  command = apply

  variables {
    ami_id = "ami-0bbbbbbbbbbbbbbb2"
  }

  assert {
    condition     = output.image_source == "given" && output.uses_fallback_image == false && output.configurations["ubuntu24-plain"].ami_id == "ami-0bbbbbbbbbbbbbbb2"
    error_message = "A given image wins."
  }
}

run "the_newest_hardened_image_is_looked_up_by_name_prefix" {
  command = apply

  variables {
    hardened_ami_name_prefix = "mls-hardened-ubuntu-24"
  }

  assert {
    condition     = output.image_source == "hardened-lookup" && output.uses_fallback_image == false
    error_message = "A name prefix means the image-builder output is used."
  }
}

run "one_configuration_can_carry_its_own_image" {
  command = apply

  variables {
    configurations = {
      "ubuntu24-plain" = {}
      "special"        = { ami_id = "ami-0cccccccccccccc03" }
    }
  }

  assert {
    condition     = output.configurations["special"].ami_id == "ami-0cccccccccccccc03" && output.configurations["ubuntu24-plain"].ami_id == "ami-0aaaaaaaaaaaaaaa1"
    error_message = "Its own image, the default for the rest."
  }

  assert {
    condition     = contains([for s in jsondecode(output.provision_policy_json).Statement : s.Sid], "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages") && contains([for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages"][0].Resource, "arn:aws:ec2:ap-south-1::image/ami-0cccccccccccccc03")
    error_message = "The role may launch from exactly the images in the catalogue."
  }
}

run "a_launch_must_carry_the_mandatory_tags_and_a_small_burstable_type" {
  command = apply

  assert {
    condition = (
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.StringEquals["aws:RequestTag/managed-by"] == "provisioning" &&
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.StringEquals["aws:RequestTag/project"] == "mls"
    )
    error_message = "Only instances tagged project=mls and managed-by=provisioning can be launched."
  }

  assert {
    condition = (
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.Null["aws:RequestTag/owner"] == "false" &&
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.Null["aws:RequestTag/ticket"] == "false" &&
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.Null["aws:RequestTag/expires-at"] == "false" &&
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.Null["aws:RequestTag/ttl"] == "false" &&
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.Null["aws:RequestTag/environment"] == "false"
    )
    error_message = "No tag, no launch: owner, ticket, expires-at, ttl and environment are all required."
  }

  assert {
    condition     = [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchOnlyTaggedSmallBurstableInstances"][0].Condition.StringEquals["ec2:InstanceType"] == ["t3.small", "t3.medium", "t3.large"]
    error_message = "Only the allowed instance types."
  }
}

run "the_launch_is_limited_to_the_named_subnets_and_group_and_encrypted_volumes" {
  command = apply

  assert {
    condition = (
      contains([for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages"][0].Resource, "arn:aws:ec2:ap-south-1:111111111111:subnet/subnet-0aaaaaaaaaaaaaaa1") &&
      contains([for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages"][0].Resource, "arn:aws:ec2:ap-south-1:111111111111:subnet/subnet-0bbbbbbbbbbbbbbb2") &&
      !contains([for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages"][0].Resource, "arn:aws:ec2:ap-south-1:111111111111:subnet/*")
    )
    error_message = "Exactly the named subnets, never subnet/*: the default VPC is out of reach."
  }

  assert {
    condition     = [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "OnlyEncryptedVolumes"][0].Condition.Bool["ec2:Encrypted"] == "true"
    error_message = "Volumes must be encrypted."
  }
}

run "terminate_is_limited_to_what_this_platform_launched" {
  command = apply

  assert {
    condition = (
      [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "TerminateOnlyWhatThisPlatformLaunched"][0].Condition.StringEquals["aws:ResourceTag/managed-by"] == "provisioning" &&
      [for s in jsondecode(output.reaper_policy_json).Statement : s if s.Sid == "TerminateOnlyWhatThisPlatformLaunched"][0].Condition.StringEquals["aws:ResourceTag/managed-by"] == "provisioning" &&
      [for s in jsondecode(output.reaper_policy_json).Statement : s if s.Sid == "TerminateOnlyWhatThisPlatformLaunched"][0].Condition.StringEquals["aws:ResourceTag/project"] == "mls"
    )
    error_message = "Both roles may terminate only instances tagged project=mls and managed-by=provisioning."
  }

  assert {
    condition     = [for s in jsondecode(output.reaper_policy_json).Statement : s.Action if s.Effect == "Allow"] == ["ec2:DescribeInstances", "ec2:TerminateInstances"]
    error_message = "The reaper can read instances and terminate them: nothing else."
  }
}

run "only_ec2_may_receive_the_environment_role_and_nothing_works_outside_mumbai" {
  command = apply

  assert {
    condition     = [for s in jsondecode(output.provision_policy_json).Statement : s if s.Sid == "HandTheEnvironmentRoleToEc2Only"][0].Condition.StringEquals["iam:PassedToService"] == "ec2.amazonaws.com"
    error_message = "PassRole only to EC2."
  }

  assert {
    condition     = [for s in jsondecode(output.provision_policy_json).Statement : s if s.Effect == "Deny"][0].Condition.StringNotEquals["aws:RequestedRegion"] == "ap-south-1"
    error_message = "A deny outside Mumbai."
  }
}

run "the_api_can_only_move_the_expiry_of_a_managed_instance" {
  command = apply

  assert {
    condition = (
      [for s in jsondecode(output.api_policy_json).Statement : s if s.Sid == "ExtendOnlyByMovingTheExpiry"][0].Condition["ForAllValues:StringEquals"]["aws:TagKeys"] == ["expires-at", "ttl"] &&
      [for s in jsondecode(output.api_policy_json).Statement : s if s.Sid == "ExtendOnlyByMovingTheExpiry"][0].Condition.StringEquals["aws:ResourceTag/managed-by"] == "provisioning"
    )
    error_message = "Extend changes expires-at and ttl on a managed instance, no other tag."
  }
}

run "the_reaper_runs_every_15_minutes_and_can_be_switched_off" {
  command = apply

  assert {
    condition     = aws_scheduler_schedule.reaper.schedule_expression == "rate(15 minutes)" && aws_scheduler_schedule.reaper.state == "ENABLED" && aws_scheduler_schedule.reaper.name == "mls-sched-dev-lab-001"
    error_message = "A 15 minute timetable, on."
  }

  assert {
    condition     = aws_scheduler_schedule.reaper.target[0].arn == "arn:aws:scheduler:::aws-sdk:sfn:startExecution" && strcontains(aws_scheduler_schedule.reaper.target[0].input, ":dev")
    error_message = "It starts the reaper through its alias."
  }
}

run "the_reaper_can_be_disabled" {
  command = apply

  variables {
    reaper_enabled = false
  }

  assert {
    condition     = aws_scheduler_schedule.reaper.state == "DISABLED"
    error_message = "Switched off means DISABLED."
  }
}

run "environments_accept_nothing_in_and_send_only_web_traffic_out_by_default" {
  command = apply

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.access) == 0
    error_message = "No inbound rule by default."
  }

  assert {
    condition     = length(aws_vpc_security_group_egress_rule.tcp) == 2 && contains(keys(aws_vpc_security_group_egress_rule.tcp), "443") && contains(keys(aws_vpc_security_group_egress_rule.tcp), "80")
    error_message = "Outbound 80 and 443 only."
  }

  assert {
    condition     = length(aws_vpc_endpoint.ssm) == 0
    error_message = "No endpoints unless asked for: they cost money every hour."
  }
}

run "a_named_group_may_reach_the_environments_on_the_named_ports" {
  command = apply

  variables {
    access_security_group_ids = ["sg-0aaaaaaaaaaaaaaa9"]
    access_ports              = { from_port = 8080, to_port = 8089 }
  }

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.access) == 1 && aws_vpc_security_group_ingress_rule.access["sg-0aaaaaaaaaaaaaaa9"].from_port == 8080 && aws_vpc_security_group_ingress_rule.access["sg-0aaaaaaaaaaaaaaa9"].to_port == 8089
    error_message = "Inbound from the named group on the named ports."
  }
}

run "private_instances_can_get_session_manager_endpoints" {
  command = apply

  variables {
    create_ssm_endpoints = true
  }

  assert {
    condition     = length(aws_vpc_endpoint.ssm) == 3 && aws_vpc_endpoint.ssm["ssm"].private_dns_enabled == true && aws_vpc_endpoint.ssm["ssmmessages"].service_name == "com.amazonaws.ap-south-1.ssmmessages" && aws_vpc_endpoint.ssm["ec2messages"].vpc_endpoint_type == "Interface"
    error_message = "ssm, ssmmessages and ec2messages as interface endpoints with private DNS."
  }
}

run "a_public_address_is_off_unless_asked_for" {
  command = apply

  assert {
    condition     = jsondecode(output.rendered_definitions.provision).States.A.Assign.publicIp == "{% false %}"
    error_message = "No public address by default."
  }
}

run "a_public_address_can_be_turned_on_for_a_network_without_nat" {
  command = apply

  variables {
    associate_public_ip = true
  }

  assert {
    condition     = jsondecode(output.rendered_definitions.provision).States.A.Assign.publicIp == "{% true %}"
    error_message = "On means on."
  }
}

run "the_environment_role_has_session_manager_and_nothing_else" {
  command = apply

  assert {
    condition     = strcontains(aws_iam_role.environment.assume_role_policy, "ec2.amazonaws.com") && endswith(aws_iam_role_policy_attachment.environment_ssm.policy_arn, "/AmazonSSMManagedInstanceCore")
    error_message = "EC2 assumes it; Session Manager is its only permission."
  }
}

run "the_aliases_carry_the_environment_name" {
  command = apply

  assert {
    condition     = endswith(output.provision_alias_arn, ":dev") && endswith(output.reaper_alias_arn, ":dev")
    error_message = "Callers start the dev alias."
  }
}

# ---- what must be refused

run "an_instance_type_outside_the_allowed_list_is_refused" {
  command = plan

  variables {
    configurations = { "big" = { instance_type = "m6i.4xlarge" } }
  }

  expect_failures = [var.configurations]
}

run "a_non_burstable_type_cannot_be_allowed" {
  command = plan

  variables {
    allowed_instance_types = ["m6i.large"]
    configurations         = {}
  }

  expect_failures = [var.allowed_instance_types]
}

run "more_than_72_hours_is_refused" {
  command = plan

  variables {
    max_ttl_hours = 100
  }

  expect_failures = [var.max_ttl_hours]
}

run "a_default_life_longer_than_the_maximum_is_refused" {
  command = plan

  variables {
    default_ttl_hours = 80
  }

  expect_failures = [var.default_ttl_hours]
}

run "another_postgres_version_is_refused" {
  command = plan

  variables {
    configurations = { "pg17" = { postgres_package = "postgresql-17" } }
  }

  expect_failures = [var.configurations]
}

run "access_groups_without_ports_are_refused" {
  command = plan

  variables {
    access_security_group_ids = ["sg-0aaaaaaaaaaaaaaa9"]
  }

  expect_failures = [var.access_ports]
}

run "no_subnet_is_refused" {
  command = plan

  variables {
    subnet_ids = []
  }

  expect_failures = [var.subnet_ids]
}

run "another_region_is_refused" {
  command = plan

  variables {
    region = "us-east-1"
  }

  expect_failures = [var.region]
}

# A template naming a placeholder this module does not fill makes templatestring fail the plan ("vars map does not contain key").
# That error comes from a local value, which a test cannot expect, so it was checked by hand and is not asserted here.

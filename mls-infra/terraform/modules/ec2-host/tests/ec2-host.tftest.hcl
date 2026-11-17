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

  mock_resource "aws_instance" {
    defaults = {
      id  = "i-0123456789abcdef0"
      arn = "arn:aws:ec2:ap-south-1:111111111111:instance/i-0123456789abcdef0"
    }
  }
}

variables {
  name      = "mls-ec2-dev-mgmt-001"
  vpc_id    = "vpc-0aaaaaaaaaaaaaaa1"
  subnet_id = "subnet-0aaaaaaaaaaaaaaa1"
  tags      = { project = "mls", environment = "dev" }
}

run "instance_is_locked_down_by_default" {
  command = plan

  assert {
    condition     = aws_instance.this.metadata_options[0].http_tokens == "required" && aws_instance.this.metadata_options[0].http_put_response_hop_limit == 1 && aws_instance.this.metadata_options[0].instance_metadata_tags == "disabled"
    error_message = "IMDSv2 only, one hop, no tags in metadata."
  }

  assert {
    condition     = aws_instance.this.associate_public_ip_address == false && aws_instance.this.monitoring == true && aws_instance.this.ebs_optimized == true
    error_message = "No public address unless asked for; detailed monitoring and EBS optimisation on."
  }

  assert {
    condition     = aws_instance.this.instance_initiated_shutdown_behavior == "stop"
    error_message = "A shutdown from inside must stop the instance, not terminate it."
  }
}

run "root_volume_is_encrypted_gp3" {
  command = plan

  assert {
    condition     = aws_instance.this.root_block_device[0].encrypted == true && aws_instance.this.root_block_device[0].volume_type == "gp3" && aws_instance.this.root_block_device[0].volume_size == 100 && aws_instance.this.root_block_device[0].delete_on_termination == true
    error_message = "Encrypted gp3 of the requested size."
  }
}

run "no_inbound_and_web_only_outbound_by_default" {
  command = plan

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.this) == 0
    error_message = "There must be no inbound rule by default."
  }

  assert {
    condition     = length(aws_vpc_security_group_egress_rule.tcp) == 2 && contains(keys(aws_vpc_security_group_egress_rule.tcp), "443") && contains(keys(aws_vpc_security_group_egress_rule.tcp), "80")
    error_message = "Outbound is limited to ports 80 and 443."
  }
}

run "role_has_session_manager_and_nothing_else_by_default" {
  command = plan

  assert {
    condition     = endswith(aws_iam_role_policy_attachment.ssm.policy_arn, "/AmazonSSMManagedInstanceCore") && length(aws_iam_role_policy_attachment.extra) == 0
    error_message = "Only the Session Manager policy is attached by default."
  }

  assert {
    condition     = strcontains(aws_iam_role.this.assume_role_policy, "ec2.amazonaws.com")
    error_message = "The role is assumed by EC2."
  }
}

run "image_is_looked_up_once_and_never_replaces_the_instance" {
  command = plan

  assert {
    condition     = length(data.aws_ami.ubuntu) == 1 && output.ami_id == "ami-0aaaaaaaaaaaaaaa1"
    error_message = "Without a pinned image the Ubuntu image is looked up."
  }
}

run "a_pinned_image_skips_the_lookup" {
  command = plan

  variables {
    ami_id = "ami-0bbbbbbbbbbbbbbb2"
  }

  assert {
    condition     = length(data.aws_ami.ubuntu) == 0 && output.ami_id == "ami-0bbbbbbbbbbbbbbb2"
    error_message = "A pinned image is used as given."
  }
}

run "burstable_instance_can_be_held_to_standard_credits" {
  command = plan

  variables {
    instance_type = "t3.xlarge"
    cpu_credits   = "standard"
  }

  assert {
    condition     = length(aws_instance.this.credit_specification) == 1 && aws_instance.this.credit_specification[0].cpu_credits == "standard"
    error_message = "A t-series instance asked for standard credits must carry them."
  }
}

run "credit_setting_is_ignored_for_other_families" {
  command = plan

  variables {
    instance_type = "m6i.xlarge"
    cpu_credits   = "standard"
  }

  assert {
    condition     = length(aws_instance.this.credit_specification) == 0
    error_message = "Credits apply to burstable instances only."
  }
}

run "no_credit_setting_unless_asked_for" {
  command = plan

  variables {
    instance_type = "t3.xlarge"
  }

  assert {
    condition     = length(aws_instance.this.credit_specification) == 0
    error_message = "Without cpu_credits the AWS default applies."
  }
}

run "rejects_an_unknown_credit_mode" {
  command = plan

  variables {
    instance_type = "t3.xlarge"
    cpu_credits   = "turbo"
  }

  expect_failures = [var.cpu_credits]
}

run "extras_can_be_added_on_purpose" {
  command = plan

  variables {
    associate_public_ip = true
    imds_hop_limit      = 2
    root_volume_gb      = 200
    managed_policy_arns = ["arn:aws:iam::111111111111:policy/mls-deploy-allow", "arn:aws:iam::111111111111:policy/mls-deploy-guardrails"]
    ingress_rules = [
      { description = "Test ports from the agent", from_port = 8080, to_port = 8089, source_security_group_id = "sg-0aaaaaaaaaaaaaaa1" }
    ]
  }

  assert {
    condition     = aws_instance.this.associate_public_ip_address == true && aws_instance.this.metadata_options[0].http_put_response_hop_limit == 2 && aws_instance.this.root_block_device[0].volume_size == 200
    error_message = "Public address, two hops and a bigger disk were asked for."
  }

  assert {
    condition     = length(aws_iam_role_policy_attachment.extra) == 2 && length(aws_vpc_security_group_ingress_rule.this) == 1
    error_message = "Two extra policies and one inbound rule."
  }
}

run "timetable_starts_and_stops_this_instance_only" {
  command = apply

  variables {
    schedule = {
      enabled    = true
      start_cron = "cron(30 10 ? * MON-FRI *)"
      stop_cron  = "cron(30 19 ? * MON-FRI *)"
    }
  }

  assert {
    condition     = aws_scheduler_schedule.start[0].schedule_expression == "cron(30 10 ? * MON-FRI *)" && aws_scheduler_schedule.stop[0].schedule_expression == "cron(30 19 ? * MON-FRI *)" && aws_scheduler_schedule.start[0].schedule_expression_timezone == "Asia/Kolkata" && aws_scheduler_schedule.stop[0].schedule_expression_timezone == "Asia/Kolkata"
    error_message = "Weekday start and stop in India time."
  }

  assert {
    condition     = endswith(aws_scheduler_schedule.start[0].target[0].arn, "aws-sdk:ec2:startInstances") && endswith(aws_scheduler_schedule.stop[0].target[0].arn, "aws-sdk:ec2:stopInstances")
    error_message = "The schedules call EC2 start and stop."
  }

  assert {
    condition     = strcontains(aws_iam_role_policy.scheduler[0].policy, "ec2:StartInstances") && strcontains(aws_iam_role_policy.scheduler[0].policy, "ec2:StopInstances") && strcontains(aws_iam_role_policy.scheduler[0].policy, aws_instance.this.arn) && !strcontains(aws_iam_role_policy.scheduler[0].policy, "\"*\"")
    error_message = "The timetable role may only start and stop, on one instance."
  }

  assert {
    condition     = length(output.schedule_names) == 2
    error_message = "Two schedules."
  }
}

run "no_timetable_by_default" {
  command = plan

  assert {
    condition     = length(aws_scheduler_schedule.start) == 0 && length(aws_scheduler_schedule.stop) == 0 && length(aws_iam_role.scheduler) == 0
    error_message = "No schedule unless enabled."
  }
}

run "rejects_an_enabled_timetable_without_times" {
  command = plan

  variables {
    schedule = { enabled = true }
  }

  expect_failures = [var.schedule]
}

run "rejects_a_timetable_that_is_not_cron" {
  command = plan

  variables {
    schedule = { enabled = true, start_cron = "rate(1 hour)", stop_cron = "cron(0 19 ? * * *)" }
  }

  expect_failures = [var.schedule]
}

run "rejects_an_inbound_rule_open_to_the_internet" {
  command = plan

  variables {
    ingress_rules = [{ description = "Everyone", from_port = 22, to_port = 22, cidr_ipv4 = "0.0.0.0/0" }]
  }

  expect_failures = [var.ingress_rules]
}

run "rejects_an_inbound_rule_with_two_sources" {
  command = plan

  variables {
    ingress_rules = [{ description = "Two", from_port = 22, to_port = 22, cidr_ipv4 = "10.0.0.0/8", source_security_group_id = "sg-0aaaaaaaaaaaaaaa1" }]
  }

  expect_failures = [var.ingress_rules]
}

run "rejects_a_bad_instance_type_image_and_disk" {
  command = plan

  variables {
    instance_type  = "big"
    ami_id         = "not-an-image"
    root_volume_gb = 5
  }

  expect_failures = [var.instance_type, var.ami_id, var.root_volume_gb]
}

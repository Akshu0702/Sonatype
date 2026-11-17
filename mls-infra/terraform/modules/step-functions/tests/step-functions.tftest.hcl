mock_provider "aws" {
  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  # Real-looking identifiers, because the mock would otherwise invent strings that are not valid ARNs.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::111111111111:role/mls-sfn-dev-lab-001"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:ap-south-1:111111111111:log-group:/aws/vendedlogs/states/mls-sfn-dev-lab-001"
    }
  }

  mock_resource "aws_sfn_state_machine" {
    defaults = {
      arn                       = "arn:aws:states:ap-south-1:111111111111:stateMachine:mls-sfn-dev-lab-001"
      state_machine_version_arn = "arn:aws:states:ap-south-1:111111111111:stateMachine:mls-sfn-dev-lab-001:1"
    }
  }

  mock_resource "aws_sfn_alias" {
    defaults = {
      arn = "arn:aws:states:ap-south-1:111111111111:stateMachine:mls-sfn-dev-lab-001:dev"
    }
  }
}

variables {
  name       = "mls-sfn-dev-lab-001"
  account_id = "111111111111"
  tags       = { project = "mls", environment = "dev" }
  definition = jsonencode({ StartAt = "A", States = { A = { Type = "Pass", End = true } } })
  policy_json = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "ec2:DescribeInstances", Resource = "*" }]
  })
}

run "versions_are_published_and_logs_carry_no_payload" {
  command = plan

  assert {
    condition     = aws_sfn_state_machine.this.publish == true && aws_sfn_state_machine.this.type == "STANDARD"
    error_message = "Every change must publish a version of a standard state machine."
  }

  assert {
    condition     = aws_sfn_state_machine.this.logging_configuration[0].include_execution_data == false && aws_sfn_state_machine.this.logging_configuration[0].level == "ERROR"
    error_message = "Execution data is never logged; errors are."
  }

  assert {
    condition     = aws_cloudwatch_log_group.this.name == "/aws/vendedlogs/states/mls-sfn-dev-lab-001" && aws_cloudwatch_log_group.this.retention_in_days == 90
    error_message = "Logs go to the vended-logs group, kept 90 days."
  }
}

run "role_can_only_be_assumed_by_this_state_machine" {
  command = plan

  assert {
    condition     = strcontains(aws_iam_role.this.assume_role_policy, "states.amazonaws.com") && strcontains(aws_iam_role.this.assume_role_policy, "\"aws:SourceAccount\":\"111111111111\"")
    error_message = "Only the Step Functions service, only for this account."
  }

  assert {
    condition     = strcontains(aws_iam_role.this.assume_role_policy, "stateMachine:mls-sfn-dev-lab-001*")
    error_message = "Only this state machine."
  }
}

run "the_tasks_policy_is_exactly_what_the_caller_passed" {
  command = plan

  assert {
    condition     = aws_iam_role_policy.tasks.policy == var.policy_json
    error_message = "The module adds no permission of its own beyond log delivery."
  }
}

run "no_alias_unless_asked_for" {
  command = plan

  assert {
    condition     = length(aws_sfn_alias.this) == 0
    error_message = "Without aliases none is created."
  }
}

# apply, not plan: the alias ARN is only known once the (mocked) alias exists.
run "an_alias_points_all_traffic_at_the_new_version" {
  command = apply

  variables {
    aliases = ["dev"]
  }

  assert {
    condition     = length(aws_sfn_alias.this) == 1 && aws_sfn_alias.this["dev"].routing_configuration[0].weight == 100
    error_message = "The alias takes every execution."
  }

  assert {
    condition     = output.alias_arns["dev"] == "arn:aws:states:ap-south-1:111111111111:stateMachine:mls-sfn-dev-lab-001:dev"
    error_message = "The alias ARN is an output."
  }
}

run "failed_timed_out_and_aborted_executions_are_alarmed" {
  command = plan

  variables {
    alarm_topic_arns = ["arn:aws:sns:ap-south-1:111111111111:mls-sns-dev-lab-001"]
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.bad_end) == 3 && aws_cloudwatch_metric_alarm.bad_end["failed"].metric_name == "ExecutionsFailed" && aws_cloudwatch_metric_alarm.bad_end["timed_out"].metric_name == "ExecutionsTimedOut" && aws_cloudwatch_metric_alarm.bad_end["aborted"].metric_name == "ExecutionsAborted"
    error_message = "One alarm for each bad ending."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.bad_end["failed"].namespace == "AWS/States" && aws_cloudwatch_metric_alarm.bad_end["failed"].threshold == 0 && aws_cloudwatch_metric_alarm.bad_end["failed"].treat_missing_data == "notBreaching"
    error_message = "A single failure raises the alarm; silence is not a breach."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.bad_end["failed"].alarm_actions) == 1
    error_message = "The alarm tells the topic."
  }
}

run "a_name_without_the_mls_prefix_is_refused" {
  command = plan

  variables {
    name = "other-sfn-dev-lab-001"
  }

  expect_failures = [var.name]
}

run "a_definition_that_is_not_a_state_machine_is_refused" {
  command = plan

  variables {
    definition = "{\"hello\": 1}"
  }

  expect_failures = [var.definition]
}

run "another_region_is_refused" {
  command = plan

  variables {
    region = "us-east-1"
  }

  expect_failures = [var.region]
}

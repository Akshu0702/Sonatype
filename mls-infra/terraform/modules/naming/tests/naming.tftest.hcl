variables {
  resource_type = "ec2"
  environment   = "dev"
  function      = "web"
  owner         = "platform"
}

run "builds_the_standard_name" {
  command = plan

  assert {
    condition     = output.name == "mls-ec2-dev-web-001"
    error_message = "Expected mls-ec2-dev-web-001."
  }
}

run "sets_the_standard_tags" {
  command = plan

  assert {
    condition = (
      output.tags["project"] == "mls" &&
      output.tags["environment"] == "dev" &&
      output.tags["function"] == "web" &&
      output.tags["owner"] == "platform" &&
      output.tags["managed-by"] == "terraform" &&
      output.tags["data-class"] == "none"
    )
    error_message = "Standard tags are missing or wrong."
  }

  assert {
    condition     = output.tags_with_name["Name"] == "mls-ec2-dev-web-001"
    error_message = "The Name tag must hold the resource name."
  }
}

run "standard_tags_win_over_extra_tags" {
  command = plan

  variables {
    extra_tags = { owner = "someone-else", cost-code = "x1" }
  }

  assert {
    condition     = output.tags["owner"] == "platform" && output.tags["cost-code"] == "x1"
    error_message = "Standard tags must win and extra tags must be kept."
  }
}

run "region_and_long_name_are_tags_not_name_parts" {
  command = plan

  variables {
    region    = "ap-south-1"
    name_long = "mls-s3-dev-store-001-a1b2"
  }

  assert {
    condition     = output.tags["region"] == "ap-south-1" && output.tags["name-long"] == "mls-s3-dev-store-001-a1b2" && output.name == "mls-ec2-dev-web-001"
    error_message = "Region and name-long belong in tags only."
  }
}

run "rejects_an_unknown_environment" {
  command = plan

  variables {
    environment = "staging"
  }

  expect_failures = [var.environment]
}

run "rejects_an_unknown_function" {
  command = plan

  variables {
    function = "database"
  }

  expect_failures = [var.function]
}

run "rejects_a_person_as_owner" {
  command = plan

  variables {
    owner = "someone@example.com"
  }

  expect_failures = [var.owner]
}

run "rejects_a_bad_id" {
  command = plan

  variables {
    id = "1"
  }

  expect_failures = [var.id]
}

run "rejects_a_name_that_is_too_long" {
  command = plan

  variables {
    max_length = 10
  }

  expect_failures = [output.name]
}

run "accepts_the_uat_environment" {
  command = plan

  variables {
    environment = "uat"
  }

  assert {
    condition     = output.name == "mls-ec2-uat-web-001"
    error_message = "uat must be an accepted environment code."
  }
}

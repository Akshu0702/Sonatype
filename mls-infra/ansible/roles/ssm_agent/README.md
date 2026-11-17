# ssm_agent

Confirms that the Systems Manager agent is present and makes sure it runs and starts at boot. Hosts are administered through Systems Manager (Session Manager and Run Command), so no host needs inbound SSH.

The role does not install the agent: it ships in the standard Amazon Linux and Ubuntu AMIs, and a missing agent fails the run so the image is fixed instead. Variable: `ssm_agent_require_present`.

The role is skipped in the container tests (`ssm_agent_enabled: false`), which have no agent or init system.

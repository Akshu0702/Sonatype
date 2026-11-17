# auditd

Installs the Linux audit daemon, sets its log retention and installs the lab's audit rules: time changes, users and privileges, logins and sessions, SSH configuration, kernel module loading and file deletion by users.

Variables start with `auditd_`; see `defaults/main.yml`. Set `auditd_immutable: true` to lock the rules until reboot (a rule change then needs a reboot).

Audit logs are shipped to the log platform by the log-shipping role, which is a separate change. Loading rules and running the daemon need a real kernel; the container tests do not exercise them (`auditd_manage_services: false`).

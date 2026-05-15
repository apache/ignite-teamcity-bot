TC Bot JIRA (service) module
--------------------------------
Module for pure (non cached) requests to JIRA service

JIRA authentication is configured in `branches.json` under `jiraServers`.
Personal Access Tokens use `authScheme: "Bearer"` and can be stored directly in `authTok`.
PasswordEncoder-protected hex values are auto-detected; set `authTokEncoded` only to force a mode.
Legacy Basic auth remains available with `authScheme: "Basic"` and a base64 `user:password` token.
If `authScheme` is omitted, encoded tokens default to Basic for compatibility and plain tokens default to Bearer.

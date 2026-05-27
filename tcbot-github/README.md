TC Bot GitHub (service) module
--------------------------------
Module for pure (non cached) requests to GitHub service

GitHub authentication is configured in `branches.json` under `gitHubConfigs`.
Personal access tokens can be stored directly in `authTok`.
PasswordEncoder-protected hex values are auto-detected; set `authTokEncoded` only to force a mode.

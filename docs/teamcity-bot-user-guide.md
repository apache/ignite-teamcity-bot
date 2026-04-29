# TeamCity bot rules and user guide

This document explains the user-facing rules used by TeamCity Bot in this project: what the bot treats as a problem, when it triggers builds, how it decides that something is a blocker, and when it comments in JIRA or sends notifications.

This guide is based on:

- the current implementation in this repository;
- the sample configuration in `conf/branches.json`;
- the official Apache Ignite Teamcity Bot wiki page:
  - https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot

If the wiki and the code disagree, this document gives priority to the current code and calls out the mismatch explicitly.

## 0. Official Reference

The official user-facing description of the bot is here:

- [Apache Ignite Teamcity Bot](https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot)

That page is useful as the official user guide. This repository shows how the current behavior is actually implemented.

## 1. What The Bot Tracks

The bot works with preconfigured `tracked branches`.

For each tracked branch, the configuration defines:

- a bot-side branch identifier such as `master` or `master-nightly`;
- one or more build chains (`chains`);
- for each chain:
  - a TeamCity server;
  - a suite or build type;
  - a TeamCity branch (`branchForRest`);
  - optionally a base branch for comparison;
  - whether builds should be triggered automatically;
  - a quiet period between auto-triggers;
  - trigger parameters.

By default, the main tracked branch for a server comes from `defaultTrackedBranch`. In the sample config, that is `master`.

## 2. What Types Of Problems The Bot Detects

The bot registers and shows several kinds of issues.

### 2.1. New Regular Test Failure

Type: `newFailure`

A test is considered a new problem when it had 5 successful runs in a row and then 4 failures in a row.

In simpler terms: the test stayed green for a while and then turned red in a stable way.

This matches the description on the wiki: a history like `...000001111...` leads to a notification, where `0` means success and `1` means failure.

### 2.2. New Failure Of A Newly Introduced Test

Type: `newContributedTestFailure`

A test is treated this way if it previously had only `missing` results and then started to run for real and failed 4 times in a row.

This helps separate "a new test introduced in the branch" from "an existing test started to fail".

### 2.3. New Stable Failure Of A Flaky Test

Type: `newFailureForFlakyTest`

If a test is already known to be flaky, the bot can still detect the case where it moved from unstable behavior to stable failure.

The pattern here is longer: 5 successful runs in the past followed by 8 failures in a row.

The wiki also explains the user-facing reason for this: if a flaky test keeps failing on the same commit, the bot raises the threshold to 8 consecutive failures to avoid spamming users with random noise.

### 2.4. Critical Suite Failure

Type: `newCriticalFailure`

If a suite gets a critical build problem, the bot treats it as a separate incident.

In the current code, the detection rule is: the suite previously had `OK` or `FAIL` results, then `CRITICAL_FAILURE` happened 3 times in a row.

Important: the wiki describes a stricter rule for critical suite failure: 5 non-critical results, then 4 critical failures in a row, with the latest run also critical. The current code in this repository is looser. This document treats the code as the source of truth, but the wiki description is still useful as the original user-level intent.

### 2.5. Trusted Suite Failure

Type: `newTrustedSuiteFailure`

Some suites are marked as `trustedSuites` in config. In the sample config those are:

- `IgniteTests24Java8_LicensesHeaders`
- `IgniteTests24Java8_CheckCodeStyle`
- `IgniteTests24Java8_Javadoc`

If such a suite starts failing, the bot highlights it as a separate important problem even if this is not a regular test failure.

### 2.6. Test With High Flaky Rate

Type: `newTestWithHighFlakyRate`

The bot can detect that a test has become too unstable.

The logic is:

- it takes `flakyRate` from config;
- the default is `20%`;
- it takes `confidence` from config;
- the default is `0.95`;
- it computes how many runs are needed for a confident decision;
- if the test stays green for a while and then the failure percentage in the next window becomes higher than `flakyRate`, the test is treated as too flaky.

### 2.7. Always Failing Test

Type: `newAlwaysFailure`

This rule is disabled by default and only works if `alwaysFailedTestDetection` is enabled in config.

When enabled, the bot looks for a test that keeps failing in a long stable sequence.

## 3. What The Bot Treats As A Blocker For A PR Or Branch

The bot has separate logic not only for "new issue", but also for "possible blocker" for merge decisions and visa generation.

### 3.1. Suite-Level Blocker

A suite is treated as a blocker if at least one of these is true:

- the suite was cancelled;
- there is a timeout;
- there is a JVM crash;
- there is a failure on metric;
- there is a compilation error;
- a Java-level deadlock was detected;
- there are other build problems in a `trusted suite`.

For some of these problems, the bot also checks the history of the base branch and adds context about how often similar failures happen there.

### 3.2. Test-Level Blocker

A failed test is treated as a blocker if:

- it has no history in the base branch;
- or it has a low fail rate in the base branch;
- and it is not considered flaky.

The hardcoded threshold for "low fail rate" is less than `4%`.

So if the test is usually green in the base branch and is not flaky, its failure in a PR or branch is treated as a blocker.

The wiki describes the same idea in more user-friendly terms: the most important failures appear in the `Possible Blockers` section, and those are the ones users should either fix or prove unrelated before moving a ticket to `Patch Available`.

### 3.3. New Tests

The bot can detect tests that do not exist in the base branch.

A test appears as a new test if:

- there is no history for it in the base branch;
- the test is not muted and not ignored;
- the bot has not already marked this test as already seen for this branch pair.

### 3.4. Long-Running New Tests

If a test is new and its average duration is more than `1 minute`, the bot marks that as an important signal.

This is not a failure by itself, but it is treated as a meaningful issue for Run All.

## 4. How The Bot Chooses The Base Branch

If the base branch is not passed explicitly:

- the bot takes `defaultTrackedBranch` from the server configuration;
- finds the corresponding chain for that tracked branch;
- uses the branch of that chain as the base branch;
- if nothing is found, falls back to the TeamCity default branch.

This affects:

- blocker detection;
- comparison with reference history;
- new test detection;
- visa and JIRA comment generation.

## 5. How The Bot Maps A PR, Branch, And JIRA Ticket

### 5.1. Regular PR

If the TeamCity branch looks like `pull/<id>/head` or `pull/<id>/merge`, the bot:

- finds the matching PR;
- tries to extract the ticket number from the PR title.

If no special `branchNumPrefix` is configured, the bot expects something like `IGNITE-12345` in the title.

This matches the Apache Ignite contribution convention: if the PR title starts with `IGNITE-...`, the bot can usually resolve the ticket automatically for JIRA commenting.

### 5.2. Branch Without A PR

If the branch looks like `ignite-12345`, the bot assumes the ticket number can be derived from the branch prefix.

In the sample config, the required prefix for this kind of branch is `ignite-`.

So:

- `ignite-12345` -> `IGNITE-12345`

### 5.3. If The Ticket Cannot Be Resolved

If the bot cannot understand the ticket:

- automatic JIRA commenting will not happen;
- for a PR, the bot expects a correct ticket number in the PR title;
- for a branch without a PR, it expects the branch name to follow the configured prefix convention.

## 6. When The Bot Triggers Builds Automatically

Auto-triggering only works for chains where `triggerBuild=true`.

The queue check runs every `10 minutes`.

The bot triggers a build only if all of the following conditions are satisfied.

### 6.1. Auto-Triggering Is Not Globally Disabled

If the system property `AUTO_TRIGGERING_BUILD_DISABLED=true` is set, no new builds are triggered.

### 6.2. Auto-Triggering Is Not Disabled During Working Hours

If the server has:

- `autoTriggeringBuildDisabledStartTime`
- `autoTriggeringBuildDisabledEndTime`

then the bot does not auto-trigger builds during working hours on weekdays.

This restriction does not apply on weekends.

### 6.3. The TeamCity Queue Is Not Too Large

If the queue size is greater than `300`, the bot does not trigger new builds.

### 6.4. There Are Enough Free Agents

The default requirements are:

- at least `15%` free agents overall;
- at least `1%` free Windows agents, if Windows agents exist at all.

### 6.5. The Bot Did Not Already Trigger The Same Build

If there is already a queued or running build of the same type for the same branch and it was triggered by the same bot user, a new one is not started.

### 6.6. The Quiet Period Has Expired

If a chain has `triggerBuildQuietPeriod`, the bot will not start the next build before that interval expires.

In the sample config:

- `master-nightly` has a quiet period of `30` minutes;
- `ignite-2.11-nightly` has a quiet period of `720` minutes.

### 6.7. Trigger Parameters

If a chain defines `triggerParameters`, the bot passes them into TeamCity.

If a parameter has `randomValue=true`, one value from `selection` is chosen randomly.

From a user point of view, this means the bot can pick one of several approved environments for nightly or repeated runs, such as one of several JDKs.

## 7. How Often The Bot Checks Things

Main background intervals:

- tracked branch issue detection: every `15 minutes`;
- queue check and auto-triggering: every `10 minutes`;
- notifications for newly found issues: `90 seconds` after registration;
- observed build checks for JIRA or visa: every `10 minutes`.

## 8. When The Bot Sends Notifications

The bot sends notifications by email and or Slack.

A recipient must satisfy all relevant filters:

- it has a subscription;
- it is allowed to access the relevant TeamCity server;
- it is subscribed to the tracked branch;
- if the channel has tag filters, the issue must contain at least one matching tag.

The bot derives issue tags from build parameters described in `filteringParameters`.

For example, notifications can be filtered by Java version or scale factor.

### Freshness Limits

The bot does not send notifications for issues that are too old.

The rules are:

- for a newly detected issue: no older than `2 hours` since detection;
- if the issue was already sent before and a new recipient appears: no older than `24 hours` since detection;
- the related build must also be reasonably fresh:
  - about `10.5 days` since build start at most.

The wiki also states the user-level expectation clearly: if a test keeps failing without becoming green again, the bot should not keep sending repeated notifications for the same continuous problem. A repeated notification becomes possible only after the test becomes stable and green again, and then turns stably red again later.

### Disabling Specific Issue Types

Each tracked branch has a `disableIssueTypes` field.

If an issue type code is added there, the bot will still detect the issue but will stop notifying about it for that branch.

Examples:

- `newFailure`
- `newContributedTestFailure`
- `newFailureForFlakyTest`
- `newCriticalFailure`
- `newTrustedSuiteFailure`
- `newTestWithHighFlakyRate`
- `newAlwaysFailure`

## 9. What Happens When A User Chooses "Start Tests And Comment JIRA"

When a user asks the bot to start tests and later post the result to JIRA, the flow is:

1. The bot triggers the requested suites in TeamCity.
2. It stores an observation for the branch and ticket.
3. Every 10 minutes it checks whether the required builds are finished.
4. If one of the observed builds ends in a cancelled or unknown state, the JIRA comment is not posted.
5. When all required builds are complete, the bot builds a visa comment.
6. The comment includes:
   - blockers;
   - new tests;
   - a link to the TeamCity build;
   - comparison with the base branch.
7. After a successful comment, the observation is removed.

If the server-side bot is not authorized for background operations, the user sees a warning that automatic JIRA notification is unavailable.

The wiki describes this as the `Start tests and comment JIRA ticket on ready` user scenario.

The wiki also allows two convenient input styles:

- specify the TeamCity branch directly, for example `pull/<number>/head`;
- or in some forms enter only the PR number.

If the ticket can already be resolved unambiguously from the PR title, the ticket field can be left empty.

## 9.1. How To Check A PR In The UI

According to the wiki, the main user flow is:

1. Open the PR or branch inspection page.
2. Find the PR and click `Inspect`, or fill in the branch form manually.
3. To compare against master:
   - leave the base branch empty;
   - specify a TeamCity branch such as `pull/<number>/head`;
   - click `Latest`.
4. To compare against another branch:
   - specify both the tested branch and the base branch in TeamCity format;
   - click `Latest`.
5. Review the failure table and the `Possible Blockers` section.

The wiki also emphasizes that the report shows the fail rate for the test based on the latest 100 runs of the base branch.

## 9.2. How To Comment JIRA

According to the wiki, the main user actions are:

1. `Comment JIRA`
2. `Trigger build and comment JIRA after finish`

For these flows, the bot expects:

- a TeamCity branch or PR number;
- a JIRA ticket number, if it cannot be resolved reliably from the PR title.

## 10. What Visa Status Contains

The current visa status contains the number of `blockers`.

That number is calculated as the sum of:

- suite-level blocker problems;
- test failures that the bot classifies as blockers.

From the wiki point of view, this is the so-called `TC Bot visa` or `TC green visa` that contributors use before moving a ticket to `PATCH AVAILABLE`.

## 10.1. Other User-Facing Capabilities Mentioned On The Wiki

The official page also describes several other useful user-facing features:

- checking a specific TeamCity build by build ID;
- viewing `Master Trends`;
- comparing `Run All` statistics for master over a selected interval;
- viewing aggregates such as `min - median - max` for tests and problems;
- keeping some history in the bot cache even after TeamCity itself has already removed it.

For the `Master Trends` page, the wiki also defines user-facing limits:

- minimum interval: `1 day`;
- maximum interval: `7 days`;
- by default it compares the current week with the previous week.

## 11. Rules Visible In The Sample Configuration

The current `conf/branches.json` shows the following user-facing rules:

- main TeamCity server: `apache`;
- main tracked branch for that server: `master`;
- trusted suites:
  - `IgniteTests24Java8_LicensesHeaders`
  - `IgniteTests24Java8_CheckCodeStyle`
  - `IgniteTests24Java8_Javadoc`
- required branch prefix for contributions without a PR: `ignite-`;
- auto-triggering enabled for `master-nightly`;
- quiet period for `master-nightly`: `30 minutes`;
- quiet period for `ignite-2.11-nightly`: `720 minutes`;
- build tags configured by:
  - `env.JAVA_HOME`
  - `TEST_SCALE_FACTOR`

## 12. Short User Summary

At a high level, the bot follows these principles:

- it looks for stable new failures, not any single noisy failure;
- it treats flaky, critical, and trusted-suite issues differently;
- it compares a PR or branch against a base branch;
- it marks something as a blocker when it either clearly breaks the suite or looks unusual relative to the base branch;
- it can recognize new tests and long-running new tests;
- it auto-triggers nightly and background builds only if queue and agent availability allow it;
- it comments in JIRA only when it can reliably map the branch to a ticket;
- it notifies only subscribed recipients and only for fresh enough problems.

## 13. Scope Of This Document

This file describes the rules that are actually implemented in the current codebase.

At the same time, it is intentionally enriched with user-facing workflows and expectations from the official wiki, including:

- how to inspect a PR correctly;
- how to use the `Possible Blockers` section;
- how to post a visa to JIRA;
- how to interpret repeated notifications;
- what additional pages and modes are available to users.

This document does not try to describe:

- every REST endpoint;
- every UI field;
- internal Ignite storage details;
- low-level synchronization details with TeamCity, JIRA, or GitHub.

If needed, the next step could be a shorter quick-start guide focused only on:

- what to click in the UI;
- how the bot understands a PR;
- why JIRA was not commented;
- why a build was not auto-triggered.

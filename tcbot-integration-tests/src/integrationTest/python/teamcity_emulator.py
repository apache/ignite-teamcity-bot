#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import base64
import copy
import json
import random
import re
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
from xml.sax.saxutils import escape


USER = "ignite.tester"
PASSWORD = "ignite-password"
TRIGGERED_RUN_ALL_SECONDS = 4
TRIGGERED_SUITE_SECONDS = 30
RUN_ALL = "IgniteTests24Java17_RunAll"
PROJECT_ID = "ApacheIgnite"
PROJECT_NAME = "Apache Ignite"
SUITES = [
    "IgniteTests24Java17_Cache1",
    "IgniteTests24Java17_ComputeGrid",
    "IgniteTests24Java17_Pds1",
    "IgniteTests24Java17_Sql"
]
SUITE_MODELS = [
    {
        "buildTypeId": "IgniteTests24Java17_Cache1",
        "tests": [
            "org.apache.ignite.cache.CachePutGetTest.testPutGet",
            "org.apache.ignite.cache.CacheRebalanceTest.testHistoricalRebalance",
            "org.apache.ignite.cache.CacheTxTest.testOptimisticTx",
            "org.apache.ignite.randomized.RandomizedCacheTest.testRandomizedCachePartitionLoss"
        ],
        "duration": 28_000
    },
    {
        "buildTypeId": "IgniteTests24Java17_ComputeGrid",
        "tests": [
            "org.apache.ignite.compute.ComputeTaskTest.testMapReduce",
            "org.apache.ignite.compute.ComputeFailoverTest.testFailover",
            "org.apache.ignite.randomized.RandomizedComputeTest.testRandomizedNodeLeftDuringReduce"
        ],
        "duration": 19_000
    },
    {
        "buildTypeId": "IgniteTests24Java17_Pds1",
        "tests": [
            "org.apache.ignite.pds.WalRecoveryTest.testRecoveryAfterRestart",
            "org.apache.ignite.pds.CheckpointTest.testCheckpointUnderLoad",
            "org.apache.ignite.randomized.RandomizedPdsTest.testRandomizedWalArchiveDelay"
        ],
        "duration": 31_000
    },
    {
        "buildTypeId": "IgniteTests24Java17_Sql",
        "tests": [
            "org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange",
            "org.apache.ignite.sql.SqlIndexTest.testIndexRebuild",
            "org.apache.ignite.randomized.RandomizedSqlTest.testRandomizedQueryCancelRace"
        ],
        "duration": 24_000
    }
]
MASTER_HISTORY = [
    {"name": "testNotFlakyAlwaysGreen", "type": "stable", "runs": ["SUCCESS", "SUCCESS"]},
    {"name": "testKnownFlakyWithMarker", "type": "flaky-with-marker", "runs": ["SUCCESS", "FAILURE"]},
    {"name": "testFlakyWithoutMarkerSingleShot", "type": "flaky-without-marker", "runs": ["FAILURE", "SUCCESS"]},
    {"name": "testDeterministicBlocker", "type": "deterministic-failure", "runs": ["FAILURE", "FAILURE"]}
]
INITIAL_BUILDS = {}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            return self.json(200, {"status": "ok", "service": "teamcity"})

        if parsed.path == "/favicon.ico":
            return self.respond(204, "image/x-icon", b"")

        if parsed.path == "/viewLog.html":
            return self.build_text_page(parse_qs(parsed.query))

        if parsed.path == "/viewQueued.html":
            return self.queued_text_page(parse_qs(parsed.query))

        if parsed.path.startswith("/buildConfiguration/"):
            return self.build_configuration_text_page(parsed.path.rsplit("/", 1)[-1], parse_qs(parsed.query))

        if parsed.path == "/app/rest/users/current":
            return self.current_user()

        if parsed.path == "/app/rest/latest/buildTypes":
            return self.build_types()

        if parsed.path.startswith("/app/rest/latest/buildTypes/id:"):
            return self.build_type(parsed.path.rsplit(":", 1)[-1])

        if parsed.path == "/app/rest/latest/projects":
            return self.projects()

        if parsed.path == "/app/rest/latest/projects/" + PROJECT_ID:
            return self.project()

        if parsed.path == "/app/rest/latest/builds":
            return self.builds(parse_qs(parsed.query))

        stats_build_id = statistics_build_id(parsed.path)

        if stats_build_id is not None:
            return self.statistics(stats_build_id)

        if parsed.path.startswith("/app/rest/latest/builds/id:"):
            return self.build(parsed.path.rsplit(":", 1)[-1])

        if parsed.path == "/app/rest/latest/problemOccurrences":
            return self.problem_occurrences(parse_qs(parsed.query))

        if parsed.path == "/app/rest/latest/testOccurrences":
            return self.test_occurrences(parse_qs(parsed.query))

        return self.json(500, {"error": "unexpected TeamCity GET", "path": self.path})

    def do_POST(self):
        parsed = urlparse(self.path)

        if parsed.path == "/app/rest/buildQueue":
            return self.trigger_build()

        if parsed.path == "/__test__/teamcity/complete-build":
            return self.complete_build()

        if parsed.path == "/__test__/teamcity/reset":
            return self.reset()

        return self.json(500, {"error": "unexpected TeamCity POST", "path": self.path})

    def current_user(self):
        if not self.basic_ok():
            return self.json(401, {"message": "Authentication required"})

        return self.xml(200, """<?xml version="1.0" encoding="UTF-8"?>
<user id="1001" username="{}" name="Ignite Integration Tester" email="ignite.tester@example.com" href="/app/rest/users/id:1001">
  <groups>
    <group key="IGNITE_COMMITTER" name="Apache Ignite Committers" href="/app/rest/userGroups/key:IGNITE_COMMITTER"/>
  </groups>
</user>""".format(USER))

    def build_types(self):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        build_types = [{"id": RUN_ALL, "name": "RunAll", "composite": True}]
        build_types.extend({"id": suite, "name": suite.replace("IgniteTests24Java17_", "")} for suite in SUITES)
        body = ['<?xml version="1.0" encoding="UTF-8"?><buildTypes count="{}">'.format(len(build_types))]

        for build_type in build_types:
            body.append('<buildType id="{}" name="{}" href="/app/rest/latest/buildTypes/id:{}"/>'.format(
                xml_attr(build_type["id"]), xml_attr(build_type["name"]), xml_attr(build_type["id"])))

        body.append("</buildTypes>")

        return self.xml(200, "".join(body))

    def build_type(self, build_type_id):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        build_types = {RUN_ALL: "RunAll"}
        build_types.update({suite: suite.replace("IgniteTests24Java17_", "") for suite in SUITES})
        name = build_types.get(build_type_id)

        if name is None:
            return self.json(404, {"message": "Build type not found", "id": build_type_id})

        return self.xml(200, full_build_type_xml(build_type_id, name, self.server.server_port))

    def projects(self):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        return self.xml(200, """<?xml version="1.0" encoding="UTF-8"?>
<projects count="1">
  <project id="{0}" name="{1}" href="/app/rest/latest/projects/{0}"/>
</projects>""".format(xml_attr(PROJECT_ID), xml_attr(PROJECT_NAME)))

    def project(self):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        build_types = [{"id": RUN_ALL, "name": "RunAll"}]
        build_types.extend({"id": suite, "name": suite.replace("IgniteTests24Java17_", "")} for suite in SUITES)
        body = ['<?xml version="1.0" encoding="UTF-8"?>',
                '<project id="{}" name="{}" href="/app/rest/latest/projects/{}"><buildTypes count="{}">'.format(
                    xml_attr(PROJECT_ID), xml_attr(PROJECT_NAME), xml_attr(PROJECT_ID), len(build_types))]

        for build_type in build_types:
            body.append('<buildType id="{}" name="{}" projectId="{}" projectName="{}" '
                        'href="/app/rest/latest/buildTypes/id:{}" '
                        'webUrl="http://127.0.0.1:{}/buildConfiguration/{}"/>'.format(
                            xml_attr(build_type["id"]), xml_attr(build_type["name"]), xml_attr(PROJECT_ID),
                            xml_attr(PROJECT_NAME), xml_attr(build_type["id"]), self.server.server_port,
                            xml_attr(build_type["id"])))

        body.append("</buildTypes></project>")

        return self.xml(200, "".join(body))

    def builds(self, query):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        self.server.advance_build_lifecycle()
        history = list(self.server.builds.items())
        locator = first(query.get("locator", [""]))
        branch = first(query.get("branch", [None]))
        build_type = build_type_from_locator(locator)
        locator_branch = branch_from_locator(locator)

        if locator_branch is not None:
            branch = locator_branch

        if build_type is not None:
            history = [(build_id, build) for build_id, build in history
                       if build.get("buildTypeId") == build_type]

        if branch is not None:
            history = [(build_id, build) for build_id, build in history
                       if normalize_branch(build.get("branchName", "<default>")) == normalize_branch(branch)]

        history.sort(key=lambda item: int(item[0]), reverse=True)

        body = ['<?xml version="1.0" encoding="UTF-8"?><builds count="{}">'.format(len(history))]
        body.extend(build_xml(build_id, build, closed=True, port=self.server.server_port,
                              all_builds=self.server.builds) for build_id, build in history)
        body.append("</builds>")

        return self.xml(200, "".join(body))

    def build(self, build_id):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        self.server.advance_build_lifecycle()
        build_id = str(build_id)
        build = self.server.builds.get(build_id)

        if build is None:
            build = find_master_history_build(build_id)

        if build is None:
            return self.json(404, {"message": "Build not found", "id": build_id})

        return self.xml(200, '<?xml version="1.0" encoding="UTF-8"?>' + build_xml(build_id, build, closed=False,
            port=self.server.server_port, all_builds=self.server.builds))

    def statistics(self, build_id):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        self.server.advance_build_lifecycle()
        build = self.server.builds.get(str(build_id)) or find_master_history_build(build_id)

        if build is None:
            return self.json(404, {"message": "Build not found", "id": build_id})

        stats = build_statistics(build)
        body = ['<?xml version="1.0" encoding="UTF-8"?><properties count="{}">'.format(len(stats))]
        body.extend('<property name="{}" value="{}"/>'.format(xml_attr(key), xml_attr(val))
                    for key, val in stats.items())
        body.append("</properties>")

        return self.xml(200, "".join(body))

    def build_text_page(self, query):
        build_id = first(query.get("buildId", [""]))
        build = self.server.builds.get(str(build_id)) or find_master_history_build(build_id)

        if build is None:
            return self.text(404, "Emulated TeamCity build not found: {}".format(build_id))

        lines = [
            "Emulated TeamCity build",
            "=======================",
            "Build ID: {}".format(build_id),
            "Build type: {}".format(build.get("buildTypeId", RUN_ALL)),
            "Branch: {}".format(build.get("branchName", "<default>")),
            "State: {}".format(build.get("state", "finished")),
            "Status: {}".format(build.get("status", "UNKNOWN")),
            "",
            "RunAll suites:",
        ]
        lines.extend(" - {}".format(suite) for suite in SUITES)
        lines.append("")
        lines.append("Tests:")

        for test in build.get("tests", []):
            lines.append(" - {status} {name} ({duration} ms)".format(
                status=test.get("status", "SUCCESS"),
                name=test.get("name", "org.apache.ignite.testsuites.EmulatedSuite.test"),
                duration=test.get("duration", 1000)))

        if not build.get("tests"):
            lines.append(" - No tests recorded yet; this build is probably still running.")

        lines.append("")
        lines.append("Problems:")

        for problem in build.get("problems", []):
            lines.append(" - {}".format(problem))

        if not build.get("problems"):
            lines.append(" - none")

        return self.text(200, "\n".join(lines) + "\n")

    def queued_text_page(self, query):
        item_id = first(query.get("itemId", [""]))
        build = self.server.builds.get(str(item_id))

        if build is None:
            return self.text(404, "Emulated TeamCity queued build not found: {}".format(item_id))

        return self.text(200, "\n".join([
            "Emulated TeamCity queued/running build",
            "=====================================",
            "Build ID: {}".format(item_id),
            "Build type: {}".format(build.get("buildTypeId", RUN_ALL)),
            "Branch: {}".format(build.get("branchName", "<default>")),
            "State: {}".format(build.get("state", "running")),
            "Status: {}".format(build.get("status", "UNKNOWN"))
        ]) + "\n")

    def build_configuration_text_page(self, build_type_id, query):
        build_types = {RUN_ALL: "RunAll"}
        build_types.update({suite: suite.replace("IgniteTests24Java17_", "") for suite in SUITES})
        name = build_types.get(build_type_id)
        branch = first(query.get("branch", ["<default>"]))

        if name is None:
            return self.text(404, "Emulated TeamCity build configuration not found: {}".format(build_type_id))

        lines = [
            "Emulated TeamCity build configuration",
            "=====================================",
            "ID: {}".format(build_type_id),
            "Name: {}".format(name),
            "Project: {} ({})".format(PROJECT_NAME, PROJECT_ID),
            "Branch: {}".format(branch),
            "",
            "RunAll test set:" if build_type_id == RUN_ALL else "Suite test set:",
        ]

        if build_type_id == RUN_ALL:
            lines.extend(" - {}".format(suite) for suite in SUITES)
        else:
            lines.append(" - org.apache.ignite.testsuites.{}.testHappyPath".format(name))
            lines.append(" - org.apache.ignite.testsuites.{}.testDeterministicBlocker".format(name))

        return self.text(200, "\n".join(lines) + "\n")

    def problem_occurrences(self, query):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        self.server.advance_build_lifecycle()
        build_id = build_id_from_locator(query)
        build = self.server.builds.get(str(build_id)) or find_master_history_build(build_id)
        problems = [] if build is None else build.get("problems", [])
        body = ['<?xml version="1.0" encoding="UTF-8"?><problemOccurrences count="{}">'.format(len(problems))]

        for idx, problem in enumerate(problems, start=1):
            body.append('<problemOccurrence id="{}" type="TC_FAILED_TESTS" identity="{}" '
                        'href="/app/rest/latest/problemOccurrences/problem:(id:{}),build:(id:{})" '
                        'details="{}"><build id="{}"/></problemOccurrence>'.format(
                            idx, xml_attr(problem), idx, xml_attr(build_id), xml_attr(problem), xml_attr(build_id)))

        body.append("</problemOccurrences>")

        return self.xml(200, "".join(body))

    def test_occurrences(self, query):
        if not self.read_ok():
            return self.json(401, {"message": "Authentication required"})

        self.server.advance_build_lifecycle()
        build_id = build_id_from_locator(query)
        build = self.server.builds.get(str(build_id)) or find_master_history_build(build_id)
        tests = [] if build is None else build.get("tests", [])
        body = ['<?xml version="1.0" encoding="UTF-8"?><testOccurrences count="{}">'.format(len(tests))]

        for idx, test in enumerate(tests, start=1):
            name = test.get("name", "org.apache.ignite.testsuites.EmulatedSuite.test{}".format(idx))
            status = test.get("status", "SUCCESS")
            body.append('<testOccurrence id="id:{},build:(id:{})" name="{}" status="{}" duration="{}" '
                        'href="/app/rest/latest/testOccurrences/id:{},build:(id:{})" muted="false" '
                        'currentlyMuted="false" currentlyInvestigated="false" ignored="false">'
                        '<test id="{}"/><build id="{}"/></testOccurrence>'.format(
                            idx, xml_attr(build_id), xml_attr(name), xml_attr(status), test.get("duration", 1000),
                            idx, xml_attr(build_id), idx, xml_attr(build_id)))

        body.append("</testOccurrences>")

        return self.xml(200, "".join(body))

    def trigger_build(self):
        if not self.basic_ok():
            return self.json(401, {"message": "Authentication required"})

        payload = self.read_trigger_request()
        self.server.next_build += 1
        build_id = str(self.server.next_build)
        branch = payload.get("branchName", "pull/12001/head")
        suite = payload.get("buildTypeId", RUN_ALL)
        planned_failed_tests = randomized_failed_tests(build_id, branch, [suite] if suite != RUN_ALL else None)

        if suite == RUN_ALL:
            build = create_run_all_chain(self.server.builds, build_id, branch, "UNKNOWN", "queued",
                                         failed_tests=planned_failed_tests)
            build["plannedFailedTests"] = sorted(planned_failed_tests)
            build["autoLifecycle"] = True
            build["queuedEpoch"] = time.time()
            build["estimatedTotalSeconds"] = TRIGGERED_RUN_ALL_SECONDS
            build["currentStageText"] = "Queued emulated Ignite RunAll suites"
            self.server.next_build = int(build_id) + len(SUITE_MODELS)
        else:
            build = create_suite_build(build_id, suite, branch, "UNKNOWN", "queued",
                                       failed_tests=planned_failed_tests)
            build["plannedFailedTests"] = sorted(planned_failed_tests)
            build["autoLifecycle"] = True
            build["queuedEpoch"] = time.time()
            build["estimatedTotalSeconds"] = TRIGGERED_SUITE_SECONDS
            build["currentStageText"] = "Queued emulated suite {}".format(suite)
            self.server.builds[build_id] = build

        return self.xml(200, '<?xml version="1.0" encoding="UTF-8"?>' + build_xml(build_id, build, closed=False,
            port=self.server.server_port, all_builds=self.server.builds))

    def complete_build(self):
        payload = self.read_json()
        build_id = str(payload["buildId"])
        build = self.server.builds.get(build_id)

        if build is None:
            return self.json(404, {"message": "Build not found", "id": build_id})

        planned_failed_tests = set(build.get("plannedFailedTests", []))
        status = payload.get("status")

        if status is None:
            status = "FAILURE" if planned_failed_tests else "SUCCESS"

        failed_tests = set(test.get("name") for test in payload.get("tests", []) if test.get("status") == "FAILURE")
        if not failed_tests and not ("status" in payload and status == "SUCCESS"):
            failed_tests = planned_failed_tests

        build["state"] = "finished"
        build["status"] = status
        build["finishDate"] = tc_date()
        build["autoLifecycle"] = False

        if build.get("buildTypeId") == RUN_ALL:
            complete_run_all_dependencies(self.server.builds, build, status, failed_tests)
        else:
            completed = create_suite_build(build_id, build.get("buildTypeId"), build.get("branchName"),
                                           status, "finished", failed_tests=failed_tests)
            build["tests"] = payload.get("tests", completed["tests"])
            build["problems"] = payload.get("problems", completed["problems"])

        if "tests" in payload:
            build["tests"] = payload["tests"]

        if "problems" in payload:
            build["problems"] = payload["problems"]

        return self.json(200, build_json(build_id, build, self.server.server_port))

    def reset(self):
        self.server.next_build = 900000
        self.server.builds = initial_builds()

        return self.json(200, {"status": "reset", "service": "teamcity"})

    def basic_ok(self):
        expected = "Basic " + base64.b64encode((USER + ":" + PASSWORD).encode("utf-8")).decode("ascii")

        return self.headers.get("Authorization") == expected

    def read_ok(self):
        auth = self.headers.get("Authorization")

        return auth is None or auth.strip() in ["Basic", "Basic null"] or self.basic_ok()

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))

        return {} if length == 0 else json.loads(self.rfile.read(length).decode("utf-8"))

    def read_trigger_request(self):
        length = int(self.headers.get("Content-Length", "0"))

        if length == 0:
            return {}

        body = self.rfile.read(length).decode("utf-8")
        build_type = re.search(r"<buildType\s+[^>]*id=\"([^\"]+)\"", body)
        branch = re.search(r"<build\s+[^>]*branchName=\"([^\"]+)\"", body)

        return {
            "buildTypeId": build_type.group(1) if build_type else RUN_ALL,
            "branchName": branch.group(1) if branch else "pull/12001/head"
        }

    def json(self, status, payload):
        self.respond(status, "application/json", json.dumps(payload, sort_keys=True).encode("utf-8"))

    def xml(self, status, payload):
        self.respond(status, "application/xml", payload.encode("utf-8"))

    def text(self, status, payload):
        self.respond(status, "text/plain; charset=utf-8", payload.encode("utf-8"))

    def respond(self, status, content_type, body):
        print("[teamcity:{}] {} {} -> {} {} {}".format(self.server.server_port, self.command, self.path, status,
            content_type, self.auth_state()), flush=True)
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def auth_state(self):
        auth = self.headers.get("Authorization")

        if auth is None:
            return "auth=missing"

        if auth.strip() in ["Basic", "Basic null"]:
            return "auth=guest"

        return "auth=ok" if self.basic_ok() else "auth=bad"

    def log_message(self, fmt, *args):
        return


class Server(ThreadingHTTPServer):
    def __init__(self, address):
        super().__init__(address, Handler)
        self.next_build = 900000
        self.builds = initial_builds()

    def advance_build_lifecycle(self):
        now = time.time()

        for build_id, build in list(self.builds.items()):
            if not build.get("autoLifecycle"):
                continue

            state = build.get("state")

            if state == "queued" and now >= build.get("queuedEpoch", now) + 1.0:
                self.start_auto_build(build)
            elif state == "running" and now >= build.get("startEpoch", now) + build.get("estimatedTotalSeconds", 4):
                self.finish_auto_build(build_id, build)

    def start_auto_build(self, build):
        build["state"] = "running"
        build["status"] = "UNKNOWN"
        build["startDate"] = tc_date()
        build["startEpoch"] = time.time()
        build["estimatedTotalSeconds"] = build.get("estimatedTotalSeconds", 4)
        build["currentStageText"] = build.get("currentStageText", "Running emulated Ignite RunAll suites")

        for dep_id in build.get("snapshotDependencies", []):
            dep = self.builds.get(dep_id)

            if dep is None:
                continue

            dep["state"] = "running"
            dep["status"] = "UNKNOWN"
            dep["startDate"] = build["startDate"]
            dep["startEpoch"] = build["startEpoch"]
            dep["estimatedTotalSeconds"] = max(2, int(build.get("estimatedTotalSeconds", 4)))
            dep["currentStageText"] = "Running emulated suite {}".format(dep.get("buildTypeId"))

    def finish_auto_build(self, build_id, build):
        failed_tests = set(build.get("plannedFailedTests", []))
        status = "FAILURE" if failed_tests else "SUCCESS"

        build["state"] = "finished"
        build["status"] = status
        build["finishDate"] = tc_date()
        build["autoLifecycle"] = False

        if build.get("buildTypeId") == RUN_ALL:
            complete_run_all_dependencies(self.builds, build, status, failed_tests)
        else:
            completed = create_suite_build(build_id, build.get("buildTypeId"), build.get("branchName"),
                                           status, "finished", failed_tests=failed_tests)
            build["tests"] = completed["tests"]
            build["problems"] = completed["problems"]
            build["autoLifecycle"] = False


def first(values):
    return values[0] if values else None


def normalize_branch(branch):
    return "<default>" if branch in [None, "", "default", "<default>"] else branch


def build_type_from_locator(locator):
    if not locator:
        return None

    match = re.search(r"buildType:\(id:([^)]+)\)", locator)

    return match.group(1) if match else None


def branch_from_locator(locator):
    if not locator:
        return None

    match = re.search(r"branch:([^,]+)", locator)

    return match.group(1) if match else None


def first_randomized_test(model):
    for test in model["tests"]:
        if "Randomized" in test or "randomized" in test.lower():
            return test

    return model["tests"][-1]


def randomized_failed_tests(build_id, branch, suite_ids=None):
    models = SUITE_MODELS

    if suite_ids is not None:
        suite_ids = set(suite_ids)
        models = [model for model in SUITE_MODELS if model["buildTypeId"] in suite_ids]

    candidates = [first_randomized_test(model) for model in models]

    if not candidates:
        return set()

    rng = random.Random("{}:{}".format(build_id, branch))
    fail_count = 1 if len(candidates) == 1 else rng.randint(1, min(2, len(candidates)))

    return set(rng.sample(candidates, fail_count))


def initial_builds():
    builds = {}

    create_master_history(builds)
    create_run_all_chain(builds, "800101", "pull/12005/head", "SUCCESS", "finished",
                         queued="20260510T090000+0000", started="20260510T090010+0000",
                         finished="20260510T090130+0000")
    create_run_all_chain(builds, "800201", "pull/12006/head", "FAILURE", "finished",
                         queued="20260510T091500+0000", started="20260510T091510+0000",
                         finished="20260510T091710+0000",
                         suite_statuses={"IgniteTests24Java17_Sql": "FAILURE"},
                         failed_tests={"org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"})
    create_run_all_chain(builds, "800301", "<default>", "SUCCESS", "finished",
                         queued="20260510T084500+0000", started="20260510T084510+0000",
                         finished="20260510T084650+0000")

    return builds


def create_master_history(builds):
    base_id = 810000

    for idx, hist in enumerate(MASTER_HISTORY, start=1):
        build_id = str(base_id + idx * 10)
        failed = hist["runs"][-1] == "FAILURE"
        model = SUITE_MODELS[(idx - 1) % len(SUITE_MODELS)]
        failed_tests = set()

        if failed:
            failed_tests.add(first_randomized_test(model))

        create_run_all_chain(builds, build_id, "<default>", "FAILURE" if failed else "SUCCESS", "finished",
                             queued="20260510T0{}0000+0000".format(idx),
                             started="20260510T0{}0010+0000".format(idx),
                             finished="20260510T0{}0110+0000".format(idx),
                             suite_statuses={model["buildTypeId"]: "FAILURE" if failed else "SUCCESS"},
                             failed_tests=failed_tests)


def create_run_all_chain(builds, build_id, branch, status, state, queued=None, started=None, finished=None,
                         suite_statuses=None, failed_tests=None):
    dep_ids = [str(int(build_id) + idx) for idx in range(1, len(SUITE_MODELS) + 1)]
    now = tc_date()
    suite_statuses = suite_statuses or {}
    failed_tests = failed_tests or set()
    run_all = {
        "buildTypeId": RUN_ALL,
        "branchName": branch,
        "status": status,
        "state": state,
        "queuedDate": queued or now,
        "startDate": started if state != "queued" else None,
        "finishDate": finished if state == "finished" else None,
        "snapshotDependencies": dep_ids,
        "tests": [],
        "problems": [],
        "duration": sum(model["duration"] for model in SUITE_MODELS) + 7_000
    }

    builds[str(build_id)] = run_all

    for dep_id, model in zip(dep_ids, SUITE_MODELS):
        suite_status = suite_statuses.get(model["buildTypeId"], "SUCCESS" if status == "FAILURE" else status)
        builds[dep_id] = create_suite_build(dep_id, model["buildTypeId"], branch, suite_status, state, model,
                                            queued or now, started or now, finished if state == "finished" else None,
                                            failed_tests)

    if state == "finished":
        deps = [builds[dep_id] for dep_id in dep_ids]
        run_all["tests"] = [test for dep in deps for test in dep.get("tests", [])]
        run_all["problems"] = [problem for dep in deps for problem in dep.get("problems", [])]

    return run_all


def create_suite_build(build_id, suite, branch, status, state, model=None, queued=None, started=None, finished=None,
                       failed_tests=None):
    model = model or suite_model(suite)
    failed_tests = failed_tests or set()
    now = tc_date()
    tests = []

    for name in model["tests"]:
        if status == "FAILURE" and failed_tests:
            test_status = "FAILURE" if name in failed_tests else "SUCCESS"
        else:
            test_status = status if status in ["SUCCESS", "FAILURE"] else "SUCCESS"

        tests.append({
            "name": name,
            "status": test_status,
            "duration": max(500, int(model["duration"] / max(1, len(model["tests"]))))
        })

    return {
        "buildTypeId": suite,
        "branchName": branch,
        "status": status,
        "state": state,
        "queuedDate": queued or now,
        "startDate": started if state != "queued" else None,
        "finishDate": finished if state == "finished" else None,
        "tests": tests if state == "finished" else [],
        "problems": suite_problems(suite, status, failed_tests) if state == "finished" else [],
        "duration": model["duration"]
    }


def complete_run_all_dependencies(builds, run_all, status, failed_tests=None):
    dep_ids = run_all.get("snapshotDependencies")
    failed_tests = failed_tests or set()

    if not dep_ids:
        dep_ids = [str(max(int(build_id) for build_id in builds.keys()) + idx)
                   for idx in range(1, len(SUITE_MODELS) + 1)]
        run_all["snapshotDependencies"] = dep_ids

    for dep_id, model in zip(dep_ids, SUITE_MODELS):
        suite_failed_tests = set(test for test in failed_tests if test in model["tests"])
        suite_status = "FAILURE" if suite_failed_tests else ("SUCCESS" if status == "FAILURE" else status)
        dep = builds.get(dep_id) or create_suite_build(dep_id, model["buildTypeId"], run_all["branchName"],
                                                       suite_status, "running", model)
        dep["state"] = "finished"
        dep["status"] = suite_status
        dep["finishDate"] = dep.get("finishDate") or tc_date()
        dep["autoLifecycle"] = False
        dep["tests"] = create_suite_build(dep_id, model["buildTypeId"], run_all["branchName"], suite_status,
                                          "finished", model, failed_tests=suite_failed_tests)["tests"]
        dep["problems"] = suite_problems(model["buildTypeId"], suite_status, suite_failed_tests)
        builds[dep_id] = dep

    run_all["duration"] = sum(builds[dep_id].get("duration", 0) for dep_id in dep_ids) + 7_000
    run_all["tests"] = [test for dep_id in dep_ids for test in builds[dep_id].get("tests", [])]
    run_all["problems"] = [problem for dep_id in dep_ids for problem in builds[dep_id].get("problems", [])]
    run_all["status"] = "FAILURE" if run_all["problems"] else status


def suite_model(suite):
    for model in SUITE_MODELS:
        if model["buildTypeId"] == suite:
            return model

    return {
        "buildTypeId": suite,
        "tests": [
            "org.apache.ignite.testsuites.{}.testHappyPath".format(suite.replace("IgniteTests24Java17_", "")),
            "org.apache.ignite.testsuites.{}.testDeterministicBlocker".format(
                suite.replace("IgniteTests24Java17_", ""))
        ],
        "duration": 15_000
    }


def suite_problems(suite, status, failed_tests=None):
    if status != "FAILURE":
        return []

    if failed_tests:
        return ["{}: failed {}".format(suite, test) for test in sorted(failed_tests)]

    return ["{}: deterministic blocker in {}".format(suite, suite.replace("IgniteTests24Java17_", ""))]


def master_history_build(idx, branch):
    hist = MASTER_HISTORY[idx - 1]
    failed = hist["runs"][-1] == "FAILURE"

    return str(800000 + idx), {
        "buildTypeId": RUN_ALL,
        "branchName": branch,
        "status": "FAILURE" if failed else "SUCCESS",
        "state": "finished",
        "queuedDate": tc_date(),
        "startDate": tc_date(),
        "finishDate": tc_date(),
        "tests": [{
            "name": "org.apache.ignite.testsuites.EmulatedRunAll.{}".format(hist["name"]),
            "status": "FAILURE" if failed else "SUCCESS",
            "duration": 1250
        }],
        "problems": ["{}: {}".format(hist["type"], hist["name"])] if failed else []
    }


def find_master_history_build(build_id):
    try:
        idx = int(build_id) - 800000
    except ValueError:
        return None

    if idx < 1 or idx > len(MASTER_HISTORY):
        return None

    return master_history_build(idx, "<default>")[1]


def build_id_from_locator(query):
    locator = first(query.get("locator", [""]))
    match = re.search(r"build:\(id:(\d+)\)", locator)

    return match.group(1) if match else "0"


def statistics_build_id(path):
    match = re.fullmatch(r"/app/rest/latest/builds/id:(\d+)/statistics", path)

    return match.group(1) if match else None


def xml_attr(value):
    return escape(str(value), {'"': "&quot;"})


def build_json(build_id, build, port):
    res = dict(build)
    res["id"] = int(build_id)
    res["webUrl"] = build.get("webUrl", "http://127.0.0.1:{}/viewLog.html?buildId={}".format(port, build_id))

    return res


def full_build_type_xml(build_type_id, name, port):
    composite = build_type_id == RUN_ALL
    settings = {
        "artifactRules": "report.html" if composite else "work/log => logs.zip",
        "buildConfigurationType": "COMPOSITE" if composite else "REGULAR",
        "buildNumberCounter": "7008" if composite else "6082",
        "checkoutMode": "ON_SERVER",
        "excludeDefaultBranchChanges": "true",
        "showDependenciesChanges": "true"
    }
    parameters = {
        "reverse.dep.*.env.JAVA_HOME": "%env.JDK_ORA_18%",
        "system.IGNITE_DUMP_THREADS_ON_FAILURE": "false",
        "TEST_SCALE_FACTOR": "1.0",
        "TEST_SUITE": name
    }
    body = ['<?xml version="1.0" encoding="UTF-8"?>',
            '<buildType id="{0}" name="{1}" projectId="{2}" projectName="{3}" '
            'href="/app/rest/latest/buildTypes/id:{0}" '
            'webUrl="http://127.0.0.1:{4}/buildConfiguration/{0}">'.format(
                xml_attr(build_type_id), xml_attr(name), xml_attr(PROJECT_ID), xml_attr(PROJECT_NAME), port),
            '<project id="{0}" name="{1}" href="/app/rest/latest/projects/{0}" '
            'webUrl="http://127.0.0.1:{2}/project.html?projectId={0}"/>'.format(
                xml_attr(PROJECT_ID), xml_attr(PROJECT_NAME), port),
            '<templates count="0"/>',
            '<vcs-root-entries count="1"><vcs-root-entry id="GitHubApacheIgnite">'
            '<vcs-root id="GitHubApacheIgnite" name="(proxy) GitHub [apache/ignite]" '
            'href="/app/rest/vcs-roots/id:GitHubApacheIgnite"/>'
            '<checkout-rules>-:.</checkout-rules></vcs-root-entry></vcs-root-entries>',
            properties_xml("settings", settings),
            properties_xml("parameters", parameters,
                           href="/app/rest/latest/buildTypes/id:{}/parameters".format(xml_attr(build_type_id))),
            '<steps count="0"/><features count="0"/><triggers count="0"/>']

    if composite:
        body.append('<snapshot-dependencies count="{}">'.format(len(SUITE_MODELS)))

        for model in SUITE_MODELS:
            suite_id = model["buildTypeId"]
            suite_name = suite_id.replace("IgniteTests24Java17_", "")
            body.append('<snapshot-dependency id="{0}" type="snapshot_dependency">'
                        '<properties count="5">'
                        '<property name="run-build-if-dependency-failed" value="RUN_ADD_PROBLEM"/>'
                        '<property name="run-build-if-dependency-failed-to-start" value="MAKE_FAILED_TO_START"/>'
                        '<property name="run-build-on-the-same-agent" value="false"/>'
                        '<property name="take-started-build-with-same-revisions" value="true"/>'
                        '<property name="take-successful-builds-only" value="true"/>'
                        '</properties>'
                        '<source-buildType id="{0}" name="{1}" projectName="{2}" projectId="{3}" '
                        'href="/app/rest/latest/buildTypes/id:{0}" '
                        'webUrl="http://127.0.0.1:{4}/buildConfiguration/{0}"/>'
                        '</snapshot-dependency>'.format(
                            xml_attr(suite_id), xml_attr(suite_name), xml_attr(PROJECT_NAME),
                            xml_attr(PROJECT_ID), port))

        body.append("</snapshot-dependencies>")
    else:
        body.append('<snapshot-dependencies count="0"/>')

    body.append("</buildType>")

    return "".join(body)


def properties_xml(tag, properties, href=None):
    href_attr = ' href="{}"'.format(xml_attr(href)) if href else ""
    body = ['<{0} count="{1}"{2}>'.format(tag, len(properties), href_attr)]

    for key, value in properties.items():
        body.append('<property name="{}" value="{}"/>'.format(xml_attr(key), xml_attr(value)))

    body.append("</{}>".format(tag))

    return "".join(body)


def build_xml(build_id, build, closed, port=None, all_builds=None):
    web_url = build.get("webUrl")

    if web_url is None and port is not None:
        web_url = "http://127.0.0.1:{}/viewLog.html?buildId={}".format(port, build_id)
    elif web_url is None:
        web_url = "/viewLog.html?buildId={}".format(build_id)

    attrs = {
        "id": build_id,
        "buildTypeId": build["buildTypeId"],
        "branchName": build.get("branchName", "<default>"),
        "status": tc_rest_status(build),
        "state": build.get("state", "finished"),
        "href": "/app/rest/latest/builds/id:{}".format(build_id),
        "webUrl": web_url
    }

    if build.get("buildTypeId", "").endswith("_RunAll"):
        attrs["composite"] = "true"

    attr_text = " ".join('{}="{}"'.format(key, xml_attr(val)) for key, val in attrs.items())

    if closed:
        return "<build {}/>".format(attr_text)

    date_elements = "".join("<{0}>{1}</{0}>".format(name, xml_attr(build[name]))
                            for name in ["queuedDate", "startDate", "finishDate"] if build.get(name))
    running_info = running_info_xml(build)
    deps = build.get("snapshotDependencies", [])
    snapshot_dependencies = ""

    if deps:
        snapshot_dependencies = '<snapshot-dependencies count="{}">'.format(len(deps))
        for dep_id in deps:
            dep = (all_builds or {}).get(dep_id)
            dep_type = dep.get("buildTypeId", RUN_ALL) if dep else RUN_ALL
            dep_branch = dep.get("branchName", build.get("branchName", "<default>")) if dep else build.get(
                "branchName", "<default>")
            dep_status = tc_rest_status(dep) if dep else "SUCCESS"
            dep_state = dep.get("state", "running") if dep else "running"
            dep_web_url = "http://127.0.0.1:{}/viewLog.html?buildId={}".format(port, dep_id) if port is not None \
                else "/viewLog.html?buildId={}".format(dep_id)
            snapshot_dependencies += ('<build id="{0}" buildTypeId="{1}" branchName="{2}" status="{3}" '
                                      'state="{4}" href="/app/rest/latest/builds/id:{0}" '
                                      'webUrl="{5}"/>').format(
                xml_attr(dep_id), xml_attr(dep_type), xml_attr(dep_branch), xml_attr(dep_status),
                xml_attr(dep_state), xml_attr(dep_web_url))
        snapshot_dependencies += "</snapshot-dependencies>"

    tests = test_counts(build)
    problems = len(build.get("problems", []))

    return """<build {}>
  {}
  {}
  {}
  <buildType id="{}" name="{}" href="/app/rest/latest/buildTypes/id:{}"/>
  <testOccurrences count="{}" passed="{}" failed="{}" ignored="0" muted="0" href="/app/rest/latest/testOccurrences?locator=build:(id:{})"/>
  <problemOccurrences count="{}" newFailed="{}" href="/app/rest/latest/problemOccurrences?locator=build:(id:{})"/>
  <statistics href="/app/rest/latest/builds/id:{}/statistics"/>
</build>""".format(
        attr_text,
        date_elements,
        snapshot_dependencies,
        running_info,
        xml_attr(build["buildTypeId"]),
        xml_attr(build["buildTypeId"].replace("IgniteTests24Java17_", "")),
        xml_attr(build["buildTypeId"]),
        tests["count"],
        tests["passed"],
        tests["failed"],
        build_id,
        problems,
        problems,
        build_id,
        build_id
    )


def tc_rest_status(build):
    """Expose live build refs as active builds; the bot treats UNKNOWN refs as cancelled."""
    if build is not None and build.get("state") in ["queued", "running"] and build.get("status") == "UNKNOWN":
        return "SUCCESS"

    return (build or {}).get("status", "UNKNOWN")


def running_info_xml(build):
    if build.get("state") != "running":
        return ""

    start_epoch = build.get("startEpoch", time.time())
    elapsed = max(1, int(time.time() - start_epoch))
    estimated_total = max(1, int(build.get("estimatedTotalSeconds", 4)))
    percent = min(99, max(1, int(elapsed * 100 / estimated_total)))
    left = max(0, estimated_total - elapsed)
    stage = build.get("currentStageText", "Running emulated TeamCity build")

    return ('<running-info percentageComplete="{0}" elapsedSeconds="{1}" estimatedTotalSeconds="{2}" '
            'leftSeconds="{3}" currentStageText="{4}" outdated="false" probablyHanging="false" '
            'lastActivityTime="{5}"/>').format(
        percent, elapsed, estimated_total, left, xml_attr(stage), xml_attr(tc_date()))


def test_counts(build):
    tests = build.get("tests", [])
    failed = sum(1 for test in tests if test.get("status") == "FAILURE")

    return {
        "count": len(tests),
        "failed": failed,
        "passed": len(tests) - failed
    }


def build_statistics(build):
    duration = build.get("duration")

    if duration is None:
        duration = sum(test.get("duration", 0) for test in build.get("tests", []))

    return {
        "BuildDuration": duration,
        "BuildDurationNetTime": max(0, duration - 5_000),
        "buildStageDuration:sourcesUpdate": 2_000,
        "buildStageDuration:artifactsPublishing": 1_000,
        "buildStageDuration:dependenciesResolving": 2_000
    }


def tc_date():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S+0000")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8013)
    args = parser.parse_args()

    server = Server((args.host, args.port))
    print("READY teamcity {}:{}".format(args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

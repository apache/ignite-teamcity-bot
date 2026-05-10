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
import copy
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse


TOKEN = "jira-test-token"
EMPTY_TEST_BASIC_AUTH = "Basic "
TEST_BASIC_AUTH = "Basic aWduaXRlLnRlc3RlcjpqaXJhLXRlc3QtdG9rZW4="
ISSUES = {
    "IGNITE-20001": {
        "summary": "Fix rebalance progress under node restart",
        "status": "Patch Available",
        "assignee": "ignite.tester",
        "labels": ["pull-request-available"]
    },
    "IGNITE-20002": {
        "summary": "Document cache metrics for historical rebalance",
        "status": "Open",
        "assignee": "docs.owner",
        "labels": []
    },
    "IGNITE-20005": {
        "summary": "Already validated SQL retry cleanup",
        "status": "Patch Available",
        "assignee": "ignite.tester",
        "labels": ["pull-request-available"]
    },
    "IGNITE-20006": {
        "summary": "Break SQL retry on contributor branch",
        "status": "Patch Available",
        "assignee": "unhappy-contributor",
        "labels": ["pull-request-available"]
    }
}
COMMENTS = {
    "IGNITE-20005": ["TC Bot emulated visa: RunAll completed successfully for pull/12005/head."]
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            return self.json(200, {"status": "ok", "service": "jira"})

        if parsed.path == "/favicon.ico":
            return self.respond(204, "image/x-icon", b"")

        if parsed.path.startswith("/browse/"):
            return self.issue_text_page(parsed.path.rsplit("/", 1)[-1])

        if parsed.path == "/rest/api/2/search":
            if not self.auth_ok():
                return self.json(401, {"errorMessages": ["You are not authenticated. Authentication required."]})

            issues = [jira_issue_payload(self, key, issue) for key, issue in self.server.issues.items()]

            return self.json(200, {
                "startAt": 0,
                "maxResults": 100,
                "total": len(issues),
                "issues": issues
            })

        if parsed.path.startswith("/rest/api/2/issue/"):
            if not self.auth_ok():
                return self.json(401, {"errorMessages": ["You are not authenticated. Authentication required."]})

            if parsed.path.endswith("/comment"):
                key = parsed.path.split("/")[-2]

                if key not in self.server.issues:
                    return self.json(404, {"errorMessages": ["Issue Does Not Exist"], "errors": {}})

                comments = self.server.comments.get(key, [])

                return self.json(200, {
                    "startAt": 0,
                    "maxResults": len(comments),
                    "total": len(comments),
                    "comments": [jira_comment_payload(self, key, idx, body)
                                 for idx, body in enumerate(comments, start=1)]
                })

            key = parsed.path.rsplit("/", 1)[-1]
            issue = self.server.issues.get(key)

            if issue is None:
                return self.json(404, {"errorMessages": ["Issue Does Not Exist"], "errors": {}})

            return self.json(200, jira_issue_payload(self, key, issue))

        return self.json(500, {"error": "unexpected JIRA GET", "path": self.path})

    def do_POST(self):
        parsed = urlparse(self.path)

        if parsed.path == "/__test__/jira/create-issue":
            payload = self.read_json()
            key = payload["key"]
            issue = {
                "summary": payload.get("summary", "Integration test issue"),
                "status": payload.get("status", "Open"),
                "assignee": payload.get("assignee", "integration.tester"),
                "labels": payload.get("labels", [])
            }

            self.server.issues[key] = issue

            return self.json(201, issue)

        if parsed.path.startswith("/rest/api/2/issue/") and parsed.path.endswith("/comment"):
            if not self.auth_ok():
                return self.json(401, {"errorMessages": ["You are not authenticated. Authentication required."]})

            key = parsed.path.split("/")[-2]

            if key not in self.server.issues:
                return self.json(404, {"errorMessages": ["Issue Does Not Exist"], "errors": {}})

            body = self.read_json().get("body")

            if body is None:
                return self.json(400, {"errorMessages": [], "errors": {"body": "Body is required"}})

            self.server.comments.setdefault(key, []).append(body)

            return self.json(201, jira_comment_payload(self, key, len(self.server.comments[key]), body))

        if parsed.path == "/__test__/jira/reset":
            self.server.issues = copy.deepcopy(ISSUES)
            self.server.comments = copy.deepcopy(COMMENTS)

            return self.json(200, {"status": "reset", "service": "jira"})

        return self.json(500, {"error": "unexpected JIRA POST", "path": self.path})

    def auth_ok(self):
        auth = self.headers.get("Authorization")

        return auth is None or auth == EMPTY_TEST_BASIC_AUTH or auth == TEST_BASIC_AUTH or auth == "Bearer " + TOKEN

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))

        return {} if length == 0 else json.loads(self.rfile.read(length).decode("utf-8"))

    def json(self, status, payload):
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
        self.respond(status, "application/json", body)

    def text(self, status, payload):
        self.respond(status, "text/plain; charset=utf-8", payload.encode("utf-8"))

    def respond(self, status, content_type, body):
        print("[jira:{}] {} {} -> {} {}".format(self.server.server_port, self.command, self.path, status,
            content_type), flush=True)
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def issue_text_page(self, key):
        issue = self.server.issues.get(key)

        if issue is None:
            return self.text(404, "Emulated JIRA issue not found: {}\n".format(key))

        lines = [
            "Emulated JIRA issue",
            "===================",
            "Key: {}".format(key),
            "Summary: {}".format(issue["summary"]),
            "Status: {}".format(issue["status"]),
            "Assignee: {}".format(issue["assignee"]),
            "Labels: {}".format(", ".join(issue["labels"]) if issue["labels"] else "none"),
            "",
            "Comments:",
        ]

        for idx, body in enumerate(self.server.comments.get(key, []), start=1):
            lines.append(" #{} {}".format(idx, body))

        if not self.server.comments.get(key):
            lines.append(" none")

        return self.text(200, "\n".join(lines) + "\n")

    def log_message(self, fmt, *args):
        return


class Server(ThreadingHTTPServer):
    def __init__(self, address):
        super().__init__(address, Handler)
        self.issues = copy.deepcopy(ISSUES)
        self.comments = copy.deepcopy(COMMENTS)


def api_root(handler):
    return "http://127.0.0.1:{}/".format(handler.server.server_port)


def jira_issue_payload(handler, key, issue):
    return {
        "id": str(abs(hash(key)) % 1000000),
        "key": key,
        "self": api_root(handler) + "rest/api/2/issue/" + key,
        "fields": {
            "summary": issue["summary"],
            "description": issue.get("description", issue["summary"]),
            "customfield_11050": issue.get("customfield_11050"),
            "status": {"name": issue["status"]},
            "assignee": {"name": issue["assignee"]},
            "labels": issue["labels"]
        }
    }


def jira_comment_payload(handler, key, idx, body):
    return {
        "id": str(idx),
        "self": api_root(handler) + "rest/api/2/issue/{}/comment/{}".format(key, idx),
        "body": body,
        "author": {"name": "ignite.tester", "displayName": "Ignite Integration Tester"},
        "updateAuthor": {"name": "ignite.tester", "displayName": "Ignite Integration Tester"}
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8012)
    args = parser.parse_args()

    server = Server((args.host, args.port))
    print("READY jira {}:{}".format(args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

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


TOKEN = "github-test-token"
AVATAR_SVG = b"""<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
  <circle cx="32" cy="32" r="30" fill="#4fa657"/>
  <path fill="#fff" d="M21.5 24.5c-5.8 5.8-5.8 15.2 0 21 2.9 2.9 6.7 4.4 10.5 4.4s7.6-1.5 10.5-4.4C49.8 38.2 51 16 51 16s-22.2 1.2-29.5 8.5zm18 18c-2 2-4.6 3-7.5 3s-5.5-1-7.5-3-3-4.6-3-7.5 1-5.5 3-7.5c3.1-3.1 10.9-5.2 18.7-6-0.8 7.8-2.9 15.9-3.7 21z"/>
  <path fill="#fff" d="M27 32c1.7-1.7 4.6-3 7.6-3.8-0.8 3-2.1 5.9-3.8 7.6-1 1-2.4 1.6-3.8 1.6s-2.8-0.6-3.8-1.6c-2.1-2.1-2.1-5.6 0-7.8z"/>
</svg>"""
BRANCHES = {
    "master": {"sha": "0f0f0f0abcdef1234567890abcdef1234567890", "protected": True},
    "pull/12001/head": {"sha": "a12001fabcdef1234567890abcdef1234567890"},
    "pull/12003/head": {"sha": "a12003fabcdef1234567890abcdef1234567890"},
    "pull/12005/head": {"sha": "a12005fabcdef1234567890abcdef1234567890"},
    "pull/12006/head": {"sha": "a12006fabcdef1234567890abcdef1234567890"},
    "pull/12007/head": {"sha": "a12007fabcdef1234567890abcdef1234567890"}
}
PULL_REQUESTS = {
    12001: {
        "title": "IGNITE-20001 Fix rebalance progress under node restart",
        "head": "pull/12001/head",
        "base": {"ref": "master"},
        "user": "ignite-tester"
    },
    12003: {
        "title": "Refactor binary metadata cleanup",
        "head": "pull/12003/head",
        "base": {"ref": "master"},
        "user": "driveby-contributor"
    },
    12005: {
        "title": "IGNITE-20005 Already validated SQL retry cleanup",
        "head": "pull/12005/head",
        "base": {"ref": "master"},
        "user": "ignite-tester"
    },
    12006: {
        "title": "IGNITE-20006 Break SQL retry on contributor branch",
        "head": "pull/12006/head",
        "base": {"ref": "master"},
        "user": "unhappy-contributor"
    },
    12007: {
        "title": "IGNITE-20007 Does not compile",
        "head": "pull/12007/head",
        "base": {"ref": "master"},
        "user": "unhappy-contributor"
    }
}
USERS = {
    "ignite-tester": {
        "id": 1001,
        "name": "Ignite Integration Tester",
        "email": "ignite.tester@example.com"
    },
    "driveby-contributor": {
        "id": 1003,
        "name": "Drive-by Contributor",
        "email": None
    },
    "unhappy-contributor": {
        "id": 1006,
        "name": "Unhappy Contributor",
        "email": "unhappy-contributor@example.com"
    }
}
COMMENTS = {
    12005: ["TC Bot emulated visa: RunAll completed successfully for pull/12005/head."]
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            return self.json(200, {"status": "ok", "service": "github"})

        if parsed.path == "/favicon.ico":
            return self.respond(204, "image/x-icon", b"")

        if parsed.path.startswith("/avatars/u/"):
            return self.respond(200, "image/svg+xml", AVATAR_SVG)

        pr_web_number = pull_request_web_path(parsed.path)

        if pr_web_number is not None:
            return self.pull_request_text_page(pr_web_number)

        if parsed.path == "/repos/apache/ignite/branches":
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            return self.json(200, [branch_payload(self, name, branch)
                                   for name, branch in self.server.branches.items()])

        if parsed.path == "/repos/apache/ignite/pulls":
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            return self.json(200, [pull_request_payload(self, number, pr)
                                   for number, pr in self.server.pull_requests.items()])

        if parsed.path.startswith("/repos/apache/ignite/pulls/"):
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            number = int(parsed.path.rsplit("/", 1)[-1])
            pr = self.server.pull_requests.get(number)

            if pr is None:
                return self.json(404, {"message": "Not Found", "documentation_url": "https://docs.github.com/rest"})

            return self.json(200, pull_request_payload(self, number, pr))

        if parsed.path.startswith("/users/"):
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            login = parsed.path.rsplit("/", 1)[-1]
            user = self.server.users.get(login)

            if user is None:
                return self.json(404, {"message": "Not Found", "documentation_url": "https://docs.github.com/rest"})

            return self.json(200, user_payload(login, user, api_root(self)))

        issue_comment_match = issue_comments_path(parsed.path)

        if issue_comment_match is not None:
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            return self.json(200, [comment_payload(self, issue_comment_match, idx, body)
                                   for idx, body in enumerate(
                                       self.server.comments.get(issue_comment_match, []), start=1)])

        return self.json(500, {"error": "unexpected GitHub GET", "path": self.path})

    def do_POST(self):
        parsed = urlparse(self.path)

        if parsed.path == "/__test__/github/create-pr":
            payload = self.read_json()
            number = int(payload.get("number") or max(self.server.pull_requests.keys()) + 1)
            issue = payload.get("issue")
            title = payload.get("title") or (issue + " Integration test contribution" if issue else "Integration test PR")
            pr = {
                "title": title,
                "head": payload.get("head", "pull/{}/head".format(number)),
                "base": {"ref": payload.get("base", "master")},
                "user": payload.get("user", "integration.tester")
            }
            sha = payload.get("sha", "a{}abcdef1234567890abcdef1234567890".format(number))

            self.server.pull_requests[number] = pr
            self.server.branches[pr["head"]] = {"sha": sha}

            return self.json(201, pull_request_payload(self, number, pr))

        issue_number = issue_comments_path(parsed.path)

        if issue_number is not None:
            if not self.auth_ok():
                return self.json(401, {"message": "Bad credentials"})

            if issue_number not in self.server.pull_requests:
                return self.json(404, {"message": "Not Found", "documentation_url": "https://docs.github.com/rest"})

            body = self.read_json().get("body")

            if body is None:
                return self.json(422, {"message": "Validation Failed", "errors": [{"field": "body"}]})

            self.server.comments.setdefault(issue_number, []).append(body)

            return self.json(201, comment_payload(self, issue_number, len(self.server.comments[issue_number]), body))

        if parsed.path == "/__test__/github/reset":
            self.server.branches = copy.deepcopy(BRANCHES)
            self.server.pull_requests = copy.deepcopy(PULL_REQUESTS)
            self.server.users = copy.deepcopy(USERS)
            self.server.comments = copy.deepcopy(COMMENTS)

            return self.json(200, {"status": "reset", "service": "github"})

        return self.json(500, {"error": "unexpected GitHub POST", "path": self.path})

    def auth_ok(self):
        auth = self.headers.get("Authorization")

        return auth is None or auth == "Bearer " + TOKEN or auth == "token " + TOKEN

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))

        return {} if length == 0 else json.loads(self.rfile.read(length).decode("utf-8"))

    def json(self, status, payload):
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
        self.respond(status, "application/json", body)

    def text(self, status, payload):
        self.respond(status, "text/plain; charset=utf-8", payload.encode("utf-8"))

    def respond(self, status, content_type, body):
        safe_log("[github:{}] {} {} -> {} {}".format(self.server.server_port, self.command, self.path, status,
            content_type))
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def pull_request_text_page(self, number):
        pr = self.server.pull_requests.get(number)

        if pr is None:
            return self.text(404, "Emulated GitHub PR not found: #{}\n".format(number))

        lines = [
            "Emulated GitHub pull request",
            "============================",
            "PR: #{}".format(number),
            "Title: {}".format(pr["title"]),
            "Author: {}".format(pr["user"]),
            "Branch: {}".format(pr["head"]),
            "Base: {}".format(pr.get("base", {}).get("ref", "master")),
            "State: {}".format(pr.get("state", "open")),
            "",
            "Comments:",
        ]

        for idx, body in enumerate(self.server.comments.get(number, []), start=1):
            lines.append(" #{} {}".format(idx, body))

        if not self.server.comments.get(number):
            lines.append(" none")

        return self.text(200, "\n".join(lines) + "\n")

    def log_message(self, fmt, *args):
        return


def safe_log(message):
    try:
        print(message, flush=True)
    except OSError:
        pass


class Server(ThreadingHTTPServer):
    def __init__(self, address):
        super().__init__(address, Handler)
        self.branches = copy.deepcopy(BRANCHES)
        self.pull_requests = copy.deepcopy(PULL_REQUESTS)
        self.users = copy.deepcopy(USERS)
        self.comments = copy.deepcopy(COMMENTS)


def issue_comments_path(path):
    parts = path.strip("/").split("/")

    if len(parts) == 5 and parts[:3] == ["repos", "apache", "ignite"] and parts[3] == "issues":
        return int(parts[4]) if parts[4].isdigit() else None

    if len(parts) == 6 and parts[:3] == ["repos", "apache", "ignite"] and parts[3] == "issues" \
            and parts[5] == "comments":
        return int(parts[4]) if parts[4].isdigit() else None

    return None


def pull_request_web_path(path):
    parts = path.strip("/").split("/")

    if len(parts) == 4 and parts[:3] == ["apache", "ignite", "pull"]:
        return int(parts[3]) if parts[3].isdigit() else None

    return None


def api_root(handler):
    return "http://127.0.0.1:{}/".format(handler.server.server_port)


def branch_payload(handler, name, branch):
    sha = branch["sha"]

    return {
        "name": name,
        "commit": {
            "sha": sha,
            "url": api_root(handler) + "repos/apache/ignite/commits/" + sha
        },
        "protected": branch.get("protected", False)
    }


def pull_request_payload(handler, number, pr):
    head_ref = pr["head"]
    branch = handler.server.branches.get(head_ref, {})
    sha = branch.get("sha", "a{}abcdef1234567890abcdef1234567890".format(number))
    login = pr["user"]

    return {
        "number": number,
        "title": pr["title"],
        "state": pr.get("state", "open"),
        "url": api_root(handler) + "repos/apache/ignite/pulls/{}".format(number),
        "html_url": api_root(handler) + "apache/ignite/pull/{}".format(number),
        "head": {
            "ref": head_ref,
            "sha": sha
        },
        "base": pr.get("base", {"ref": "master"}),
        "user": user_payload(login, handler.server.users.get(login, {}), api_root(handler))
    }


def user_payload(login, user, html_root=None):
    return {
        "login": login,
        "id": user.get("id", abs(hash(login)) % 1000000),
        "name": user.get("name", login),
        "email": user.get("email"),
        "html_url": (html_root + "users/" + login) if html_root is not None else "https://github.com/" + login,
        "avatar_url": (
            html_root + "avatars/u/{}".format(user.get("id", 0))
            if html_root is not None else "https://avatars.githubusercontent.com/u/{}".format(user.get("id", 0))
        )
    }


def comment_payload(handler, number, idx, body):
    return {
        "id": number * 1000 + idx,
        "body": body,
        "url": api_root(handler) + "repos/apache/ignite/issues/{}/comments/{}".format(number, idx),
        "html_url": api_root(handler) + "apache/ignite/pull/{}#issuecomment-{}".format(number, idx),
        "user": user_payload("ignite-tester", handler.server.users["ignite-tester"], api_root(handler))
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8011)
    args = parser.parse_args()

    server = Server((args.host, args.port))
    print("READY github {}:{}".format(args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

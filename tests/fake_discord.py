#!/usr/bin/env python3
"""A tiny stand-in for discord.com/api/v9, for reproducing the post-login load.

Only the routes the client hits right after a login are served, with payloads
shaped like Discord's (guild list, user settings with guild folders, the DM
list with one regular and one group DM, and a guild's channel list).

    ./tests/fake_discord.py [PORT]      (default 8099)

Every request is printed, so a run tells which call the client reached before
it stopped.
"""
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

# FAKE_DISCORD_MODE=html answers every route with a Cloudflare-style block page,
# the shape a TLS-intercepted or filtered connection comes back as.
MODE = os.environ.get("FAKE_DISCORD_MODE", "json")

GUILD_ID = "100000000000000010"
DM_ID = "100000000000000020"
GROUP_ID = "100000000000000030"
TOKEN = "MTAwMDAwMDAwMDAwMDAwMDAx.GGGGGG.abcdef"

ROUTES = {
    "/api/v9/experiments": {
        "fingerprint": "0.0.0.fake-fingerprint",
    },
    "/api/v9/auth/login": {
        "token": TOKEN,
    },
    "/api/v9/users/@me": {
        "id": "100000000000000001",
        "username": "tester",
        "global_name": "Tester",
    },
    "/api/v9/users/@me/guilds": [
        {"id": GUILD_ID, "name": "Test Server"},
    ],
    "/api/v9/users/@me/settings": {
        "guild_folders": [
            {"guild_ids": [GUILD_ID]},
            {"guild_ids": []},
        ],
    },
    "/api/v9/users/@me/channels": [
        {
            "id": DM_ID,
            "type": 1,
            "last_message_id": "1400000000000000001",
            "recipients": [
                {"id": "100000000000000002", "username": "friend", "global_name": "Friend"},
            ],
        },
        {
            "id": GROUP_ID,
            "type": 3,
            # A DM channel that never had a message: Discord sends null here.
            "last_message_id": None,
            "name": None,
            "recipients": [
                {"id": "100000000000000003", "username": "a", "global_name": None},
                {"id": "100000000000000004", "username": "b", "global_name": "B"},
            ],
        },
    ],
    "/api/v9/guilds/%s/channels" % GUILD_ID: [
        {"id": "100000000000000040", "type": 4, "name": "Text Channels", "last_message_id": None},
        {"id": "100000000000000041", "type": 0, "name": "general",
         "last_message_id": "1400000000000000002", "parent_id": "100000000000000040"},
        {"id": "100000000000000042", "type": 0, "name": "off-topic",
         "last_message_id": None, "parent_id": "100000000000000040"},
    ],
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        self.handle_request("GET")

    def do_POST(self):
        self.handle_request("POST")

    def handle_request(self, method):
        path = self.path.split("?")[0]
        sys.stdout.write("%s %s\n" % (method, self.path))
        sys.stdout.flush()

        length = int(self.headers.get("Content-Length", 0))
        if length:
            self.rfile.read(length)

        if path == "/hang" or (MODE == "hang_login" and path == "/api/v9/auth/login"):
            # Accept the connection but never answer, like a stalled proxy.
            # The client's HTTP queue must close it after the timeout.
            time.sleep(30)
            return

        if MODE == "html":
            data = (b"<!DOCTYPE html><html><head><title>Attention Required! "
                    b"| Cloudflare</title></head><body>blocked</body></html>")
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        body = ROUTES.get(path)
        status = 200 if body is not None else 404
        if body is None:
            body = {"message": "404: Not Found", "code": 0}

        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    server = HTTPServer(("127.0.0.1", port), Handler)
    sys.stdout.write("fake discord on http://127.0.0.1:%d\n" % port)
    sys.stdout.flush()
    server.serve_forever()


if __name__ == "__main__":
    main()

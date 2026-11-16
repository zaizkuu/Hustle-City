"""Tiny client for the official Blender MCP addon socket (localhost:9876).

Protocol: null-byte-delimited JSON, {"type": "execute", "code": ..., "strict_json": false}.
The executed code reports back by filling the `result` dict and/or printing.

usage:
  bl.py <file.py> [<file2.py> ...]   run the files concatenated, in order
  bl.py -c "<code>"                  run a snippet
"""
import json
import os
import socket
import sys


def execute(code, timeout=600.0):
    s = socket.create_connection(("127.0.0.1", 9876), timeout=10)
    s.settimeout(timeout)
    s.sendall(json.dumps({"type": "execute", "code": code, "strict_json": False}).encode("utf-8") + b"\0")
    buf = bytearray()
    while b"\0" not in buf:
        chunk = s.recv(65536)
        if not chunk:
            break
        buf.extend(chunk)
    s.close()
    raw = bytes(buf).split(b"\0")[0]
    return json.loads(raw.decode("utf-8")) if raw else {"status": "error", "message": "empty response"}


def main():
    a = sys.argv[1:]
    if a and a[0] == "-c":
        code = a[1]
    else:
        # The addon reads a request 4 KB per timer tick and drops slow senders, so the scripts aren't sent
        # inline: Blender reads them from disk itself, all into the one namespace.
        paths = [os.path.abspath(p) for p in a]
        code = ("for _p in {!r}:\n"
                "    with open(_p, encoding='utf-8') as _f:\n"
                "        exec(compile(_f.read(), _p, 'exec'))\n").format(paths)
    # the scripts can't know where they live once they're inside Blender
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    r = execute("HC_ROOT = {!r}\n".format(root) + code)
    if r.get("stdout"):
        print(r["stdout"].rstrip())
    if r.get("stderr"):
        print("[stderr]", r["stderr"].rstrip())
    if r.get("status") == "ok":
        if r.get("result"):
            print(json.dumps(r["result"], indent=1))
    else:
        print("ERROR:", r.get("message"))
        sys.exit(1)


if __name__ == "__main__":
    main()

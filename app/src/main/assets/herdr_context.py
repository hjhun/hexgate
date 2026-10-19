"""Read-only Herdr context query, executed over SSH; never opens a listener."""
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


def session_argument(args, environment):
    if "--session" in args:
        index = args.index("--session")
        return args[index + 1] if index + 1 < len(args) else None
    if len(args) >= 4 and args[1:3] == ["session", "attach"]:
        return args[3]
    return environment.get("HERDR_SESSION")


def terminal_clients(session):
    found = []
    for proc in Path("/proc").iterdir():
        if not proc.name.isdigit():
            continue
        try:
            if proc.stat().st_uid != os.getuid():
                continue
            args = [s.decode("utf-8", "replace") for s in (proc / "cmdline").read_bytes().split(b"\0") if s]
            if not args or Path(args[0]).name != "herdr":
                continue
            # Read just the session selector; never return environment contents.
            environment = {}
            for field in (proc / "environ").read_bytes().split(b"\0"):
                if field.startswith(b"HERDR_SESSION="):
                    environment["HERDR_SESSION"] = field.split(b"=", 1)[1].decode()
            if session_argument(args, environment) != session:
                continue
            remaining = list(args[1:])
            if "--session" in remaining:
                i = remaining.index("--session")
                del remaining[i:i + 2]
            if remaining and remaining[0] == "server":
                continue
            if remaining and remaining[:2] != ["session", "attach"]:
                found.append({"pid": int(proc.name), "tty": "unidentified-client"})
                continue
            tty = os.readlink(proc / "fd/0")
            found.append({"pid": int(proc.name), "tty": tty if re.fullmatch(r"/dev/pts/\d+", tty) else "unidentified-client"})
        except (OSError, ValueError, UnicodeError):
            continue
    return sorted(found, key=lambda item: item["pid"])


def automatic_focus(before, after, tty):
    return before == after and len(after) == 1 and after[0]["tty"] == tty


def query(session, tty):
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", session):
        raise ValueError("Invalid session name")
    binary = shutil.which("herdr") or str(Path.home() / ".local/bin/herdr")
    before = terminal_clients(session)
    result = subprocess.run([binary, "--session", session, "api", "snapshot"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=10, check=True)
    if len(result.stdout) > 1024 * 1024:
        raise ValueError("Snapshot exceeds limit")
    payload = json.loads(result.stdout)
    snapshot = payload.get("result", {}).get("snapshot")
    if not isinstance(snapshot, dict):
        raise ValueError("Unsupported Herdr snapshot")
    after = terminal_clients(session)
    automatic = automatic_focus(before, after, tty)
    panes = []
    for pane in snapshot.get("panes", [])[:500]:
        cwd = pane.get("foreground_cwd") or pane.get("cwd")
        pane_id = pane.get("pane_id")
        if pane_id and isinstance(cwd, str) and cwd.startswith("/"):
            panes.append({"id": pane_id, "cwd": cwd, "name": pane.get("title") or pane_id})
    return {"session": session, "automatic": automatic,
            "focused": snapshot.get("focused_pane_id") if automatic else None,
            "panes": panes,
            "reason": "" if automatic else "Select a pane explicitly: this session has multiple or unidentified terminal clients."}


if __name__ == "__main__":
    try:
        print(json.dumps(query(sys.argv[1], sys.argv[2]), ensure_ascii=False))
    except Exception:
        print(json.dumps({"error": "Cannot resolve Herdr context. Check session name, installed version and Python 3."}))
        sys.exit(1)

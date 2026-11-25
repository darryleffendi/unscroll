#!/usr/bin/env python3
"""Drive the emulator without UiAutomation (which would suppress unscroll's service).

  tools/ui.py dump                 print a compact outline of the current screen
  tools/ui.py ids                  list view ids with bounds
  tools/ui.py tap <id|desc=text>[#n]  tap the first (or nth) visible node matching id or content-desc substring
  tools/ui.py tapchild <id> <n>    tap the nth visible child of the first node with that id
  tools/ui.py has <id|desc=text>   exit 0 if a matching node exists
  tools/ui.py shot <file.png>      screenshot
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
REMOTE = "/sdcard/Android/data/com.darryl.unscroll/files/dumps/ui.xml"


def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True, check=False)
    return out.stdout if binary else out.stdout.decode(errors="replace")


def dump_xml():
    adb("shell", "rm", "-f", REMOTE)
    adb("shell", "am", "broadcast", "-a", "com.darryl.unscroll.DUMP", "--es", "name", "ui")
    for _ in range(20):
        data = adb("exec-out", "cat", REMOTE, binary=True)
        if data.strip():
            return ET.fromstring(data)
        time.sleep(0.25)
    sys.exit("no dump: is the unscroll service bound? (mobile-mcp's agent suppresses it)")


def nodes(root):
    for n in root.iter("node"):
        if n.get("visible-to-user", "true") == "true":
            yield n


def bounds(n):
    l, t, r, b = map(int, re.findall(r"-?\d+", n.get("bounds")))
    return l, t, r, b


def short_id(n):
    rid = n.get("resource-id", "")
    return rid.split(":id/")[-1] if rid else ""


def match(n, query):
    if query.startswith("desc="):
        return query[5:].lower() in n.get("content-desc", "").lower()
    return short_id(n) == query


def tap(n):
    l, t, r, b = bounds(n)
    adb("shell", "input", "tap", str((l + r) // 2), str((t + b) // 2))
    print("tapped", short_id(n) or n.get("content-desc"), n.get("bounds"))


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "dump"
    if cmd == "shot":
        open(sys.argv[2], "wb").write(adb("exec-out", "screencap", "-p", binary=True))
        return
    root = dump_xml()
    if cmd == "ids":
        for n in nodes(root):
            if short_id(n):
                flags = "".join(f for f, k in (("S", "selected"), ("C", "clickable")) if n.get(k) == "true")
                print(f"{short_id(n):50s} {n.get('bounds'):28s} {flags} {n.get('content-desc','')[:40]}")
    elif cmd == "dump":
        for w in root.iter("window"):
            print("window", w.get("type"))
        seen = set()
        for n in nodes(root):
            key = short_id(n) or ("desc:" + n.get("content-desc", "")[:30] if n.get("content-desc") else "")
            if key and key not in seen:
                seen.add(key)
                print(" ", key, n.get("bounds"), "S" if n.get("selected") == "true" else "")
    elif cmd in ("tap", "has"):
        query, _, nth = sys.argv[2].partition("#")
        nth = int(nth or 0)
        for n in nodes(root):
            if match(n, query):
                if nth:
                    nth -= 1
                    continue
                if cmd == "has":
                    return
                tap(n)
                return
        sys.exit(f"not found: {sys.argv[2]}")
    elif cmd == "tapchild":
        for n in nodes(root):
            if short_id(n) == sys.argv[2]:
                kids = [k for k in n if k.get("visible-to-user", "true") == "true"]
                tap(kids[int(sys.argv[3])])
                return
        sys.exit(f"not found: {sys.argv[2]}")


if __name__ == "__main__":
    main()

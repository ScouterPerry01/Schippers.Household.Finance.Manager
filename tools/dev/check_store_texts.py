"""Checks the store texts in docs/store against each field's limit (DIST-07).

Usage: python tools/dev/check_store_texts.py [folder]
The folder defaults to docs/store; the phone app's repository passes its own docs/store.
A field is a "### Name (max N)" heading; lists say "(up to N, max M each)" and search terms
"(K terms, max M characters each)". Limits count characters, as the stores do.
"""
import os
import re
import sys

BASE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", "docs", "store")
HEADING = re.compile(r"^### (.+?) \((.+)\)$")


def number(text):
    return int(text.replace(",", "").replace(" ", "").replace(" ", ""))


def check(path):
    problems = []
    lines = open(path, encoding="utf-8").read().split("\n")
    i = 0
    while i < len(lines):
        m = HEADING.match(lines[i])
        if not m:
            i += 1
            continue
        name, rule = m.group(1), m.group(2)
        body = []
        i += 1
        while i < len(lines) and not lines[i].startswith("#"):
            body.append(lines[i])
            i += 1
        text = "\n".join(body).strip()
        if not text:
            problems.append(f"{name}: empty")
            continue
        each = re.search(r"(\d[\d ,]*) (?:terms|termes).*?max (\d[\d ,]*)", rule)
        listed = re.search(r"(?:up to|jusqu'à) (\d+), max (\d[\d ,]*)", rule)
        if each:
            count, limit = number(each.group(1)), number(each.group(2))
            terms = [t.strip() for t in text.split(";")]
            if len(terms) > count:
                problems.append(f"{name}: {len(terms)} terms, more than {count}")
            problems += [f"{name}: '{t}' is {len(t)} characters, more than {limit}" for t in terms if len(t) > limit]
        elif listed:
            count, limit = number(listed.group(1)), number(listed.group(2))
            items = [re.sub(r"^\d+\. ", "", l) for l in text.split("\n") if l.strip()]
            if len(items) > count:
                problems.append(f"{name}: {len(items)} items, more than {count}")
            problems += [f"{name}: item {n + 1} is {len(t)} characters, more than {limit}" for n, t in enumerate(items) if len(t) > limit]
        else:
            limit = number(re.search(r"max (\d[\d ,]*)", rule).group(1))
            if len(text) > limit:
                problems.append(f"{name}: {len(text)} characters, more than {limit}")
            else:
                print(f"  {name}: {len(text)} / {limit}")
    return problems


failed = False
for name in sorted(os.listdir(BASE)):
    if name.endswith(".md"):
        print(name)
        for problem in check(os.path.join(BASE, name)):
            print("  TOO LONG:", problem)
            failed = True
sys.exit(1 if failed else 0)

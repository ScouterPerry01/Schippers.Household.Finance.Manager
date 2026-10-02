"""Appends English and French messages to core/i18n, refusing keys that already exist.

Usage: python tools/dev/add_messages.py en.txt fr.txt
Each file holds key=value lines in UTF-8. In English text an apostrophe is written '' (MessageFormat);
French text uses the typographic apostrophe. Write the files with an editor, not a shell here-document.
"""
import os
import sys

BASE = os.path.join(os.path.dirname(__file__), "..", "..", "core", "i18n", "src", "main", "resources", "hfm", "i18n")


def apply(name, extra_path):
    extra = open(extra_path, encoding="utf-8").read().strip("\n")
    path = os.path.join(BASE, name)
    text = open(path, encoding="utf-8").read().rstrip("\n")
    existing = {line.split("=", 1)[0] for line in text.split("\n") if "=" in line and not line.startswith("#")}
    dupes = [line.split("=", 1)[0] for line in extra.split("\n") if "=" in line and line.split("=", 1)[0] in existing]
    assert not dupes, (name, dupes)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text + "\n\n" + extra + "\n")


apply("messages_en.properties", sys.argv[1])
apply("messages_fr.properties", sys.argv[2])
print("ok")

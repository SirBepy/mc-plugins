"""Shared JSON-writing helper for the resourcepacks generator scripts.

build_server_pack.py zips generated assets as-is, so the pack's sha1 changes on any whitespace
or line-ending difference; every caller here must produce the exact same bytes it did before
this module existed (see memory server-pack-publish-gotchas).
"""
import json


def write_json(path, data, *, mkdir=True, newline="\n"):
    if mkdir:
        path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline=newline)

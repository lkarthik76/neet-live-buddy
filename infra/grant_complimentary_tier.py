#!/usr/bin/env python3
"""
Grant or update Firestore accounts/{email} tier (complimentary / scholarship access).

Requires: gcloud CLI logged in with permission to read/write Firestore.
Uses Firestore REST API + `gcloud auth print-access-token` (no pip packages).

Valid tiers: free, pro, ultimate (matches services/tutor-go usage.go).
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

VALID_TIERS = frozenset({"free", "pro", "ultimate"})


def gcloud_access_token() -> str:
    r = subprocess.run(
        ["gcloud", "auth", "print-access-token"],
        capture_output=True,
        text=True,
        check=False,
    )
    if r.returncode != 0:
        print(r.stderr or r.stdout, file=sys.stderr)
        sys.exit("gcloud auth print-access-token failed (run: gcloud auth login)")
    return r.stdout.strip()


def encode_path_segment(s: str) -> str:
    # Encode each path segment for URL (emails contain @, etc.)
    return urllib.parse.quote(s, safe="")


def rest_patch(project: str, email: str, fields: dict, update_paths: list[str], token: str) -> tuple[int, str]:
    # (default) must stay literal in the path; only encode the email doc id segment.
    base = (
        f"https://firestore.googleapis.com/v1/projects/{project}"
        f"/databases/(default)/documents/accounts/{encode_path_segment(email)}"
    )
    mask = "&".join(f"updateMask.fieldPaths={urllib.parse.quote(p)}" for p in update_paths)
    url = f"{base}?{mask}"
    body = json.dumps({"fields": fields}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="PATCH",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def rest_create(project: str, email: str, fields: dict, token: str) -> tuple[int, str]:
    parent = (
        f"https://firestore.googleapis.com/v1/projects/{project}"
        f"/databases/(default)/documents/accounts"
    )
    q = urllib.parse.urlencode({"documentId": email})
    url = f"{parent}?{q}"
    body = json.dumps({"fields": fields}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def string_field(v: str) -> dict:
    return {"stringValue": v}


def array_field(strings: list[str]) -> dict:
    return {"arrayValue": {"values": [{"stringValue": s} for s in strings]}}


def main() -> None:
    p = argparse.ArgumentParser(description="Set Firestore account tier for complimentary access.")
    p.add_argument("email", help="Account email (same as accounts/{email} doc id)")
    p.add_argument("tier", choices=sorted(VALID_TIERS), help="Target tier")
    p.add_argument(
        "--project",
        default=os.environ.get("GOOGLE_CLOUD_PROJECT", ""),
        help="GCP project id (default: $GOOGLE_CLOUD_PROJECT)",
    )
    p.add_argument(
        "--reason",
        default="",
        help="Optional note stored as complimentaryReason (audit only; backend ignores if unknown)",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="Print actions only; do not call Firestore",
    )
    args = p.parse_args()

    email = args.email.strip().lower()
    if not email or "@" not in email:
        sys.exit("email must look like a valid address")

    project = args.project.strip()
    if not project:
        sys.exit("set --project or GOOGLE_CLOUD_PROJECT")

    tier = args.tier.lower()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    patch_fields: dict = {
        "email": string_field(email),
        "tier": string_field(tier),
    }
    patch_paths = ["email", "tier"]
    if args.reason.strip():
        patch_fields["complimentaryReason"] = string_field(args.reason.strip())
        patch_fields["complimentaryGrantedAt"] = string_field(now)
        patch_paths.extend(["complimentaryReason", "complimentaryGrantedAt"])

    create_fields = {
        "email": string_field(email),
        "tier": string_field(tier),
        "deviceIds": array_field([]),
        "linkedAt": string_field(now),
        "firebaseUid": string_field(""),
    }
    if args.reason.strip():
        create_fields["complimentaryReason"] = string_field(args.reason.strip())
        create_fields["complimentaryGrantedAt"] = string_field(now)

    print(f"Project: {project}")
    print(f"Account: accounts/{email}")
    print(f"Tier:    {tier}")
    if args.reason.strip():
        print(f"Reason:  {args.reason.strip()}")

    if args.dry_run:
        print("[dry-run] would PATCH (or CREATE if missing) with above fields")
        return

    token = gcloud_access_token()
    code, body = rest_patch(project, email, patch_fields, patch_paths, token)
    if code == 200:
        print("OK: updated existing account document.")
        return
    if code != 404:
        print(f"Firestore PATCH failed HTTP {code}\n{body}", file=sys.stderr)
        sys.exit(1)

    print("Document not found; creating minimal account document...")
    ccode, cbody = rest_create(project, email, create_fields, token)
    if ccode in (200, 201):
        print("OK: created account document with tier.")
        return
    print(f"Firestore POST failed HTTP {ccode}\n{cbody}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
    main()

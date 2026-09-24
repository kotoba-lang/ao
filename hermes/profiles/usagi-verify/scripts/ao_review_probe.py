#!/usr/bin/env python3
"""ao_review_probe.py - no_agent measurement for the ao review paths.

Measures the ao organism review branches on api.murakumo.cloud LIVE:

  case N (normal):    short answer well inside the token cap
                      -> expect review_triggered=False (one call)
  case L (near-limit):draft capped (max_tokens small so the draft hits it)
                      -> expect review_triggered=True, record reviewed

Per organism (ao/usagi, ao/kame) we record:
  http_ok, organism field, members, review_triggered, reviewed,
  completion_tokens vs max_tokens (cap-hit), wall ms.

Rules (AGENTS.md 8-questions):
  - A call that could not run (network, 5xx) is UNMEASURED, not a failure
    of the review branch. It still counts in ledger as its own line.
  - The script holds the judgment: agent only reads this output.
  - Append-only ledger: ~/.hermes/profiles/usagi-verify/workspace/ao-review-ledger.jsonl

stdout format: MEASURE<TAB>key<TAB>value lines (machine readable).
"""

import json
import os
import time
import urllib.request
import urllib.error

# Load the profile .env (cron runner does not source it).
_ENV = os.path.expanduser("~/.hermes/profiles/usagi-verify/.env")
if os.path.exists(_ENV):
    for line in open(_ENV):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))

BASE_URL = "https://api.murakumo.cloud/v1/chat/completions"
LEDGER = os.path.expanduser(
    "~/.hermes/profiles/usagi-verify/workspace/ao-review-ledger.jsonl")

ORGANISMS = ["ao/usagi", "ao/kame"]

# Case N: an answer that finishes well under the cap. 256 so even a
# chatty member (usagi fills small caps) lands under the 98% trigger.
CASE_NORMAL = {
    "messages": [{"role": "user", "content": "Reply with exactly: OK"}],
    "max_tokens": 256,
}
# Case L: force the draft to hit its cap (98% rule in reviewNeeded).
CASE_NEAR_LIMIT = {
    "messages": [{"role": "user",
                  "content": "Count from 1 to 400, comma separated, no spaces."}],
    "max_tokens": 40,
}


def _auth_headers():
    """Bearer when a key is available (cron runner env may differ from an
    interactive shell where the gateway answers authless)."""
    key = (os.environ.get("MURAKUMO_API_KEY")
           or os.environ.get("MURAKUMO_API_TOKEN"))
    return {"authorization": f"Bearer {key}"} if key else {}


def call(model, case, timeout=180):
    body = json.dumps({"model": model, "stream": False, **case}).encode()
    req = urllib.request.Request(
        BASE_URL, data=body, method="POST",
        headers={"content-type": "application/json", **_auth_headers()})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode())
            return {"http_ok": True, "status": resp.status,
                    "ms": int((time.time() - t0) * 1000), "data": data}
    except urllib.error.HTTPError as e:
        detail = ""
        try:
            detail = e.read().decode()[:500]
        except Exception:
            pass
        return {"http_ok": False, "status": e.code,
                "ms": int((time.time() - t0) * 1000), "error": detail}
    except Exception as e:  # timeout, conn refused, etc.
        return {"http_ok": False, "status": None,
                "ms": int((time.time() - t0) * 1000), "error": str(e)[:200]}


def extract(r):
    """Pull the measured fields out of a live response."""
    if not r["http_ok"]:
        return {"unmeasured": True, "status": r["status"],
                "error": r.get("error", "")}
    d = r["data"]
    m = d.get("murakumo", {}) or {}
    u = d.get("usage", {}) or {}
    return {
        "unmeasured": False,
        "organism": m.get("organism"),
        "members": m.get("members"),
        "review_triggered": m.get("review_triggered"),
        "reviewed": m.get("reviewed"),
        "completion_tokens": u.get("completion_tokens"),
        "max_tokens": r.get("max_tokens"),
        "finish": (d.get("choices") or [{}])[0].get("finish_reason"),
        "ms": r["ms"],
    }


def main():
    rows = []
    for org in ORGANISMS:
        for case_name, case in (("normal", CASE_NORMAL),
                                ("near_limit", CASE_NEAR_LIMIT)):
            r = call(org, case)
            r["max_tokens"] = case["max_tokens"]
            x = extract(r)
            row = {"ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                   "organism": org, "case": case_name, **x}
            rows.append(row)
            time.sleep(2)

    # Ledger append (one line per measurement row).
    os.makedirs(os.path.dirname(LEDGER), exist_ok=True)
    with open(LEDGER, "a") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    # Aggregates for this tick (so the report agent can diff without math).
    for row in rows:
        if row.get("unmeasured"):
            print(f"MEASURE\t{row['organism']}/{row['case']}\tUNMEASURED status={row.get('status')} err={row.get('error','')[:60]}")
        else:
            print(f"MEASURE\t{row['organism']}/{row['case']}"
                  f"\ttriggered={row['review_triggered']} reviewed={row['reviewed']}"
                  f" members={len(row['members'] or [])}"
                  f" ctok={row['completion_tokens']} cap={row['max_tokens']}"
                  f" finish={row['finish']} ms={row['ms']}")

    # Expectation checks (8-q: both directions, reason named).
    checks = []
    for row in rows:
        if row.get("unmeasured"):
            checks.append(f"WARN {row['organism']}/{row['case']}: UNMEASURED (not a branch failure)")
            continue
        key = f"{row['organism']}/{row['case']}"
        if row["case"] == "normal" and row["review_triggered"]:
            checks.append(f"FAIL {key}: normal-case review fired (expected one-call)")
        if row["case"] == "near_limit" and not row["review_triggered"]:
            checks.append(f"FAIL {key}: near-limit case did NOT trigger review")
        if row["case"] == "near_limit" and row["review_triggered"] and row["reviewed"] is False:
            checks.append(f"NOTE {key}: review fired but its answer was not adopted (draft kept)")
    for c in checks:
        print(f"CHECK\t{c}")

    ok = not any(c.startswith("FAIL") for c in checks)
    unmeasured = sum(1 for row in rows if row.get("unmeasured"))
    print(f"STATUS\tok={str(ok).lower()} measured={len(rows)-unmeasured} unmeasured={unmeasured}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

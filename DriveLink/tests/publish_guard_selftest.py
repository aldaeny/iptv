#!/usr/bin/env python3
"""
Executable self-test for the *shipped* publish guard.

It extracts the real `run:` block of the "Validate source run ..." step from
.github/workflows/drivelink-publish-release.yml (no YAML parser needed) and runs
it against stub `gh`/`git` binaries, so the guard's decisions are proven rather
than assumed:

  * happy path                          -> accepted
  * run built on a side branch          -> refused
  * head_sha not the origin/master tip  -> refused
  * head_sha not an ancestor of master  -> refused
  * only the unsigned artifact exists   -> refused
  * artifact expired                    -> refused
  * run older than MAX_RUN_AGE_DAYS     -> refused
  * run from another repository         -> refused
  * run that did not succeed            -> refused
  * malformed run_id                    -> refused

Exit code 0 = every expectation held.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/drivelink-publish-release.yml"
STEP_NAME = "- name: Validate source run"

MASTER = "a3b2e8cdf9460034cb027632316520a2a244a639"
SHA = "6310e81aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
REPO = "aldaeny/iptv"

STUB_GIT = r"""#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  fetch) exit 0 ;;
  rev-parse)
      if [[ "$2" == "origin/master" ]]; then echo "${STUB_MASTER_TIP:-""" + MASTER + r"""}"; else echo "${STUB_HEAD:-""" + MASTER + r"""}"; fi
      exit 0 ;;
  merge-base)
      # $2 == --is-ancestor, $3 == sha
      [[ "${STUB_IS_ANCESTOR:-true}" == "true" ]] && exit 0
      exit 1 ;;
  checkout) exit 0 ;;
  *) exit 0 ;;
esac
"""

STUB_GH = r"""#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "api" ]]; then
  if [[ "$2" == *"/artifacts"* ]]; then cat "$STUB_DIR/artifacts.json"; exit 0; fi
  cat "$STUB_DIR/run.json"; exit 0
fi
exit 0
"""


def extract_guard() -> str:
    lines = WORKFLOW.read_text().splitlines()
    start = next(i for i, ln in enumerate(lines) if ln.strip().startswith(STEP_NAME))
    run_at = next(i for i in range(start, len(lines)) if lines[i].strip().startswith("run:"))
    indent = len(lines[run_at]) - len(lines[run_at].lstrip())
    block: list[str] = []
    for ln in lines[run_at + 1:]:
        if ln.strip() and (len(ln) - len(ln.lstrip())) <= indent:
            break
        block.append(ln)
    script = "\n".join(block)
    if "merge-base --is-ancestor" not in script:
        sys.exit("extracted guard is missing the ancestor check — extraction is broken")
    return script


def scenario(tmp: Path, guard: str, name: str, *, run: dict, artifacts: list[dict],
             env: dict[str, str] | None = None, expect_ok: bool) -> bool:
    stub_dir = tmp / name
    stub_dir.mkdir()
    bin_dir = stub_dir / "bin"
    bin_dir.mkdir()
    for tool, body in (("git", STUB_GIT), ("gh", STUB_GH)):
        path = bin_dir / tool
        path.write_text(body)
        path.chmod(0o755)
    (stub_dir / "run.json").write_text(json.dumps(run))
    (stub_dir / "artifacts.json").write_text(json.dumps({"total_count": len(artifacts), "artifacts": artifacts}))

    full_env = {
        "PATH": f"{bin_dir}:/usr/bin:/bin",
        "HOME": str(stub_dir),
        "STUB_DIR": str(stub_dir),
        "GITHUB_ENV": str(stub_dir / "github_env"),
        "GITHUB_REPOSITORY": REPO,
        "RUN_ID": str(run.get("id", "1234567890")),
        "MAX_RUN_AGE_DAYS": "14",
        "GH_TOKEN": "stub",
        **(env or {}),
    }
    proc = subprocess.run(["bash", "-c", guard], capture_output=True, text=True, env=full_env, cwd=stub_dir)
    accepted = proc.returncode == 0
    good = accepted == expect_ok
    status = "PASS" if good else "FAIL"
    print(f"{status}  {name}: {'accepted' if accepted else 'refused'}"
          f"{'' if good else '  <-- expected ' + ('accepted' if expect_ok else 'refused')}")
    if not good:
        print("      stdout:", proc.stdout.strip().replace("\n", " | ")[:400])
        print("      stderr:", proc.stderr.strip().replace("\n", " | ")[:400])
    else:
        reason = (proc.stderr or proc.stdout).strip().splitlines()
        if reason and not expect_ok:
            print(f"      reason: {reason[-1][:160]}")
    return good


def base_run(**over) -> dict:
    run = {
        "id": 1234567890,
        "path": ".github/workflows/drivelink-android-release.yml",
        "name": "DriveLink CI & Android Release Build",
        "conclusion": "success",
        "event": "workflow_dispatch",
        "head_sha": MASTER,
        "head_branch": "master",
        "created_at": "2026-11-19T12:00:00Z",
        "repository": {"full_name": REPO},
    }
    run.update(over)
    return run


def signed(name_sha: str = MASTER, expired: bool = False) -> list[dict]:
    return [{"name": f"drivelink-release-{name_sha}", "expired": expired, "size_in_bytes": 1234}]


def main() -> int:
    if not shutil.which("bash"):
        sys.exit("bash is required for the publish guard self-test")
    guard = extract_guard()
    now = subprocess.run(["date", "-u", "+%Y-%m-%dT%H:%M:%SZ"], capture_output=True, text=True, check=True).stdout.strip()

    cases = [
        ("happy-path", dict(run=base_run(created_at=now), artifacts=signed(), expect_ok=True)),
        ("side-branch-run", dict(run=base_run(head_branch="arena/1b4c9e45-iptv", created_at=now),
                                artifacts=signed(), expect_ok=False)),
        ("not-master-tip", dict(run=base_run(head_sha=SHA, created_at=now),
                                artifacts=signed(SHA), env={"STUB_MASTER_TIP": MASTER},
                                expect_ok=False)),
        ("not-ancestor", dict(run=base_run(created_at=now), artifacts=signed(),
                              env={"STUB_IS_ANCESTOR": "false"}, expect_ok=False)),
        ("unsigned-only", dict(run=base_run(created_at=now),
                               artifacts=[{"name": f"drivelink-unsigned-{MASTER}", "expired": False}],
                               expect_ok=False)),
        ("expired-signed", dict(run=base_run(created_at=now), artifacts=signed(expired=True), expect_ok=False)),
        ("stale-run", dict(run=base_run(created_at="2026-10-01T00:00:00Z"), artifacts=signed(), expect_ok=False)),
        ("foreign-repo", dict(run=base_run(created_at=now, repository={"full_name": "someone/else"}),
                              artifacts=signed(), expect_ok=False)),
        ("same-name-wrong-workflow", dict(run=base_run(created_at=now, path=".github/workflows/other.yml"),
                                               artifacts=signed(), expect_ok=False)),
        ("failed-run", dict(run=base_run(conclusion="failure", created_at=now), artifacts=signed(), expect_ok=False)),
        ("bad-run-id", dict(run=base_run(created_at=now), artifacts=signed(),
                            env={"RUN_ID": "../../repos/x"}, expect_ok=False)),
    ]

    ok = True
    with tempfile.TemporaryDirectory(prefix="publish-guard-") as tmp:
        tmp_path = Path(tmp)
        for name, spec in cases:
            ok &= scenario(tmp_path, guard, name, **spec)
    print()
    if not ok:
        print("publish guard self-test FAILED")
        return 1
    print(f"publish guard self-test PASSED ({len(cases)} scenarios)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Expose instrumentation outcomes without requiring artifact downloads."""
import os
import re
from pathlib import Path
from xml.etree import ElementTree


def escape(value):
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


results = []
for report in sorted(Path("DriveLink/android/app/build/outputs/androidTest-results").rglob("TEST-*.xml")):
    for case in ElementTree.parse(report).iter("testcase"):
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        status = "FAIL" if failure is not None else "SKIP" if case.find("skipped") is not None else "PASS"
        detail = ""
        if failure is not None:
            detail = (failure.get("message") or failure.text or "No failure text").strip().splitlines()[0][:400]
        line = f"{status} {case.get('name', '(unknown)')}" + (f": {detail}" if detail else "")
        results.append(line)
        print(f"::{'error' if status == 'FAIL' else 'notice'}::{escape(line)}")
if not results:
    print("::warning::No instrumentation XML; do not count any projection test as passed.")
    log = Path(os.environ["RUNNER_TEMP"]) / "projection-logs/test.txt"
    if log.is_file():
        for line in [text for text in log.read_text(errors="replace").splitlines()
                     if re.search(r"Exception|[Ee]rror|[Ff]ailed", text)][:12]:
            print(f"::warning::{escape(line[:400])}")
    results.append("No instrumentation XML produced.")
if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(summary, "a", encoding="utf-8") as out:
        out.write("### Projection instrumentation\n\n" + "\n".join(f"- {line}" for line in results) + "\n")

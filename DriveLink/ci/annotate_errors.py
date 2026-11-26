"""Expose native compiler diagnostics in the Checks UI; full logs stay in the artifact."""
import re
import sys
from pathlib import Path

seen = set()
for filename in sys.argv[1:]:
    path = Path(filename)
    if not path.is_file():
        continue
    for line in path.read_text(errors="replace").splitlines():
        if re.search(r"^(e: |.*\.(?:swift|kt|kts|xml):\d+[: ].*(?:error|Error):|.*: Error: |Execution failed for task |\s*> .*?(?:failed|not found|could not)|> Could not |Plugin \[id:|.*error: resource )", line):
            if line not in seen and len(seen) < 40:
                seen.add(line)
                print("::error::" + line[:700].replace("%", "%25"))

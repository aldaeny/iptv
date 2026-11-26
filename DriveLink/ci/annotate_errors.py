#!/usr/bin/env python3
"""
Annotate build errors from log files as GitHub Actions annotations.
"""
import sys
import glob

def main():
    print("annotate_errors.py: Scanning logs for errors")

    log_files = []
    for pattern in sys.argv[1:]:
        log_files.extend(glob.glob(pattern, recursive=True))

    for log_file in log_files:
        try:
            with open(log_file, 'r', errors='ignore') as f:
                for i, line in enumerate(f, 1):
                    line = line.strip()
                    if any(kw in line for kw in ['error:', 'FAILURE:', 'Execution failed', 'BUILD FAILED']):
                        print(f"::error file={log_file},line={i}::{line[:200]}")
        except Exception:
            pass

    print("PASS: annotate_errors.py (placeholder)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
"""Writes the skipped tests and their reasons to the job summary (spec 005, FR-004 and FR-005).

Reads every JUnit XML report below the working directory, **/build/test-results/**/*.xml, and appends a "Skipped
tests" table with the columns Test and Reason to the file named by GITHUB_STEP_SUMMARY, or prints it when that is not
set; the table is also printed to the job's log. It only reports: it exits 0 whatever it finds, also when there are no reports.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# "org.junit.AssumptionViolatedException: FREEMIND_HOME is not set" is reported as "FREEMIND_HOME is not set".
EXCEPTION_PREFIX = re.compile(r"^(?:[A-Za-z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error|Violation)\s*:\s*")


def reason_of(skipped):
    message = (skipped.get("message") or skipped.text or "").strip()
    return EXCEPTION_PREFIX.sub("", message, count=1).strip()


def skipped_tests(root):
    rows = []
    for report in sorted(root.glob("**/build/test-results/**/*.xml")):
        try:
            tree = ET.parse(report)
        except (ET.ParseError, OSError) as error:
            print(f"::warning::Could not read {report}: {error}")
            continue
        for case in tree.iter("testcase"):
            skipped = case.find("skipped")
            if skipped is not None:
                rows.append((f"{case.get('classname', '')}.{case.get('name', '')}", reason_of(skipped)))
    return rows


def cell(text):
    # A table cell holds one line, and a bar would end it.
    return " ".join(text.split()).replace("|", "\\|") or "(no reason given)"


def summary(rows):
    lines = ["## Skipped tests", ""]
    if rows:
        lines += ["| Test | Reason |", "|---|---|"]
        lines += [f"| `{cell(test)}` | {cell(reason)} |" for test, reason in rows]
    else:
        lines.append("No tests were skipped.")
    return "\n".join(lines) + "\n"


def main():
    text = summary(skipped_tests(Path.cwd()))
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as file:
            file.write(text)
    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())

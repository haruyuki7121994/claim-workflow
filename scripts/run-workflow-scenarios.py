#!/usr/bin/env python3
"""Run the five challenge tests and bundle actual results, config and database audit evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

SCENARIOS = [
    (1, "Happy path", "completesHappyPath", 5),
    (2, "Rejection path", "completesRejectionPath", 4),
    (3, "Information request loop", "completesInformationRequestLoop", 8),
    (4, "Invalid transition", "rejectsInvalidTransitionWithoutAudit", 0),
    (5, "Unauthorized role", "rejectsUnauthorizedRole", 2),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="New output directory; existing directories are never overwritten")
    parser.add_argument("--maven", help="Maven executable; defaults to the repository wrapper")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--maven-arg", action="append", default=[])
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    started = datetime.now(timezone.utc)
    output = (args.output or root / "docs" / "examples" / ("workflow-run-" + started.strftime("%Y%m%dT%H%M%S%fZ"))).resolve()
    output.mkdir(parents=True, exist_ok=False)
    config = root / "src/main/resources/workflow.yaml"
    shutil.copy2(config, output / "workflow.yaml")
    selector = "WorkflowEndToEndTests#" + "+".join(s[2] for s in SCENARIOS)
    command = [args.maven or str(root / "mvnw")]
    if args.offline:
        command.append("-o")
    command += args.maven_arg
    report_tag = "workflow-evidence-" + started.strftime("%Y%m%dT%H%M%S%fZ")
    command += ["-Dtest=" + selector, "-Dworkflow.evidence-dir=" + str(output / "scenario-evidence"),
                "-Dsurefire.reportNameSuffix=" + report_tag, "test"]
    print("Running five PostgreSQL workflow scenarios; output: " + str(output), flush=True)
    with (output / "maven.log").open("w") as log:
        try:
            exit_code = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT).returncode
        except OSError as exc:
            log.write(str(exc) + "\n")
            exit_code = 127
    reports = output / "surefire"
    reports.mkdir()
    for report in (root / "target" / "surefire-reports").glob("*-" + report_tag + ".*"):
        shutil.copy2(report, reports / report.name)
    cases = {}
    for report in (output / "surefire").glob("TEST-*.xml"):
        for case in ET.parse(report).getroot().iter("testcase"):
            cases[case.attrib["name"]] = case
    results = []
    audits = []
    for number, name, method, expected_count in SCENARIOS:
        case = cases.get(method)
        status, error = "NOT_RUN", "Maven did not produce a result for this scenario; see maven.log"
        elapsed = None
        if case is not None:
            elapsed = float(case.get("time", "0"))
            status, error = "PASSED", None
            for tag, state in [("failure", "FAILED"), ("error", "ERROR"), ("skipped", "SKIPPED")]:
                node = case.find(tag)
                if node is not None:
                    status, error = state, (node.get("message") or node.text or state)
                    break
        evidence_path = output / "scenario-evidence" / (method + ".json")
        evidence = json.loads(evidence_path.read_text()) if evidence_path.exists() else {
            "testMethod": method, "claimId": None, "auditTrail": None,
            "auditExportError": "No database audit export was produced; the scenario may have failed during context initialization",
        }
        trail = evidence.get("auditTrail")
        audits.append({"scenario": number, "name": name, **evidence})
        result = {"scenario": number, "name": name, "testMethod": method, "status": status,
                  "durationSeconds": elapsed, "claimId": evidence.get("claimId"),
                  "expectedAuditEntries": expected_count,
                  "actualAuditEntries": len(trail) if isinstance(trail, list) else None,
                  "auditExported": isinstance(trail, list), "error": error}
        results.append(result)
    complete = exit_code == 0 and all(r["status"] == "PASSED" and r["auditExported"]
                                     and r["actualAuditEntries"] == r["expectedAuditEntries"] for r in results)
    summary = {"startedAt": started.isoformat(), "finishedAt": datetime.now(timezone.utc).isoformat(),
               "complete": complete, "mavenExitCode": exit_code,
               "workflowConfigSha256": hashlib.sha256(config.read_bytes()).hexdigest(), "scenarios": results}
    (output / "scenario-results.json").write_text(json.dumps(summary, indent=2) + "\n")
    (output / "audit-trails.json").write_text(json.dumps(audits, indent=2) + "\n")
    lines = ["# Workflow scenario results", "", "Run started: " + started.isoformat(), "",
             "Evidence complete: " + str(complete).lower(), "",
             "| Scenario | Result | Claim ID | Audit entries |", "| --- | --- | --- | --- |"]
    for r in results:
        lines.append(f'| {r["scenario"]}. {r["name"]} | {r["status"]} | {r["claimId"] or "Unavailable"} | '
                     f'{r["actualAuditEntries"] if r["auditExported"] else "Unavailable"} |')
    lines += ["", "Files: `workflow.yaml`, `scenario-results.json`, `audit-trails.json`, `maven.log`, and `surefire/`.", "",
              "Audit data is read from PostgreSQL. An empty array means zero persisted successful transitions; "
              "null means the database history could not be exported.", "",
              "Scenario 5 includes two valid setup transitions; the unauthorized approval adds no entry."]
    if not complete:
        lines += ["", "This run does not demonstrate five passing scenarios. Consult the JSON results and Maven log for failures."]
    (output / "RESULTS.md").write_text("\n".join(lines) + "\n")
    print("Evidence complete: " + str(complete).lower(), flush=True)
    print("Results: " + str(output / "RESULTS.md"), flush=True)
    return 0 if complete else 1


if __name__ == "__main__":
    sys.exit(main())

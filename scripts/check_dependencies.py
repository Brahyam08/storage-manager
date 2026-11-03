#!/usr/bin/env python3
"""Query OSV for coordinates in a Maven dependency:tree JSON inventory."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import urllib.request


def coordinates(node):
    result = set()
    for child in node.get("children", []):
        result.add((child["groupId"] + ":" + child["artifactId"], child["version"]))
        result.update(coordinates(child))
    return result


def audit(packages):
    findings = {}
    pending = [(name, version, None) for name, version in sorted(packages)]
    while pending:
        queries = []
        for name, version, page in pending:
            query = {"package": {"ecosystem": "Maven", "name": name}, "version": version}
            if page:
                query["page_token"] = page
            queries.append(query)
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                data=json.dumps({"queries": queries}).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=60) as response:
            results = json.load(response)["results"]
        if len(results) != len(pending):
            raise RuntimeError("Incomplete OSV response; no clean report can be issued")
        next_pages = []
        for (name, version, _), result in zip(pending, results):
            if "error" in result:
                raise RuntimeError("OSV reported an error; no clean report can be issued")
            ids = findings.setdefault((name, version), set())
            ids.update(vulnerability["id"] for vulnerability in result.get("vulns", []))
            if result.get("next_page_token"):
                next_pages.append((name, version, result["next_page_token"]))
        pending = next_pages
    return [{"package": name, "version": version, "ids": sorted(ids)}
            for (name, version), ids in sorted(findings.items()) if ids]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("--output", type=Path, default=Path("target/dependency-audit.json"))
    args = parser.parse_args()
    packages = coordinates(json.loads(args.inventory.read_text()))
    if not packages:
        raise ValueError("The dependency inventory is empty")
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "source": "https://api.osv.dev/v1/querybatch",
              "packages_checked": len(packages), "findings": audit(packages)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 1 if report["findings"] else 0


if __name__ == "__main__":
    raise SystemExit(main())

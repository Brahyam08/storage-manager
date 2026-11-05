#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

(
  # Do not let a developer's application/JVM settings override the H2 test profile.
  for spring_setting in "${!SPRING_@}"; do
    unset "$spring_setting"
  done
  unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_OPTS MAVEN_ARGS
  ./mvnw --batch-mode --no-transfer-progress verify
)
(
  cd frontend
  npm ci
  npm test
  npm run build
)
python3 -m unittest discover -s scripts -p 'test_local_backups.py'
python3 scripts/verify_docker.py

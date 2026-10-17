#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
python -m pip install -r scripts/requirements.txt
./gradlew lintDebug testDebugUnitTest assembleDebug validateContent solveLevels --console=plain
if [ "${1:-}" = "--full-game" ]; then ./gradlew verifyFullGame --console=plain; fi

param([switch]$FullGame)
$ErrorActionPreference='Stop'
python -m pip install -r "$PSScriptRoot/requirements.txt"
if($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location "$PSScriptRoot/.."
try {
    ./gradlew.bat lintDebug testDebugUnitTest assembleDebug validateContent solveLevels --console=plain
    if($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if($FullGame) { ./gradlew.bat verifyFullGame --console=plain; exit $LASTEXITCODE }
} finally { Pop-Location }

#!/usr/bin/env bash
set -euo pipefail
hexgate_tizen_root="$(cd "$(dirname "$0")/.." && pwd)"
hexgate_app="$hexgate_tizen_root/src/Hexgate.App"
hexgate_configuration="${CONFIGURATION:-Debug}"
# The SDK-skills runner currently reports success for tz pack without creating a .NET TPK.
# Use the verified Calendar staging path with the installed legacy Tizen .NET packager.
tizen build-cs -C "$hexgate_configuration" -- "$hexgate_app"
hexgate_stage=$(mktemp -d /tmp/hexgate-tizen-stage.XXXXXX)
hexgate_output=$(mktemp -d /tmp/hexgate-tizen-package.XXXXXX)
trap 'rm -rf "$hexgate_stage" "$hexgate_output"' EXIT
python3 - "$hexgate_app/bin/$hexgate_configuration/net8.0" "$hexgate_stage" "$hexgate_app" <<'PY'
from pathlib import Path
import shutil,sys
output,stage,app=map(Path,sys.argv[1:])
for path in output.iterdir():
 if path.is_file():shutil.copy2(path,stage/path.name)
shutil.copytree(output/'res',stage/'res',ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
p=stage/'build.info'
p.write_text('\n'.join('project-path='+str(stage) if s.startswith('project-path=') else s for s in p.read_text().splitlines())+'\n')
shutil.copy2(app/'tizen-manifest.xml',stage/'tizen-manifest.xml')
PY
tizen package -t tpk -e "$hexgate_stage" -o "$hexgate_output" -- "$hexgate_stage"
python3 - "$hexgate_output" "$hexgate_tizen_root/dist" <<'PY'
from pathlib import Path
from zipfile import ZipFile,is_zipfile
import shutil,sys,hashlib
output,destination=map(Path,sys.argv[1:]);files=[p for p in output.iterdir() if p.is_file() and is_zipfile(p)]
assert len(files)==1, 'Expected one signed TPK'
with ZipFile(files[0]) as z:
 assert z.testzip() is None
 assert {'tizen-manifest.xml','author-signature.xml','signature1.xml'}<=set(z.namelist())
 assert b'org.tizen.hexgate' in z.read('tizen-manifest.xml')
destination.mkdir(parents=True,exist_ok=True)
target=destination/'org.tizen.hexgate-0.1.0.tpk';shutil.copy2(files[0],target)
(target.parent/'SHA256SUMS').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
print(target)
PY

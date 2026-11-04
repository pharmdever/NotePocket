#!/usr/bin/env bash
set -euo pipefail
mkdir -p qa-output
collect() {
  adb pull /sdcard/Android/data/com.notepocket.notes.debug/files/screenshots qa-output/screenshots || true
  adb logcat -d -t 200 > qa-output/logcat.txt || true
  python3 - <<'PYSCREEN'
from pathlib import Path
import base64
for image in sorted(Path('qa-output/screenshots').glob('*.png')):
    print('NOTEPOCKET_SCREENSHOT:' + image.name + ':' + base64.b64encode(image.read_bytes()).decode())
PYSCREEN
}
trap collect EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e notClass com.notepocket.app.UpdatePersistenceTest com.notepocket.notes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/device-tests.txt
python3 - <<'PY'
from pathlib import Path
text=Path('qa-output/device-tests.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY
# Same package and same signing key: reinstall without uninstalling or clearing app data.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e class com.notepocket.app.UpdatePersistenceTest com.notepocket.notes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/update-tests.txt
python3 - <<'PY'
from pathlib import Path
text=Path('qa-output/update-tests.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY

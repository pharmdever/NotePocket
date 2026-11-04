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
    encoded=base64.b64encode(image.read_bytes()).decode()
    chunks=[encoded[i:i+12000] for i in range(0,len(encoded),12000)]
    for index,chunk in enumerate(chunks):
        print(f'NOTEPOCKET_SCREENSHOT:{image.name}:{index}:{len(chunks)}:{chunk}')
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
# Raise the version code while keeping the package and key, then install without clearing data.
gradle assembleDebug -PnotePocketVersionCode=3
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e class com.notepocket.app.UpdatePersistenceTest com.notepocket.notes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/update-tests.txt
python3 - <<'PY'
from pathlib import Path
text=Path('qa-output/update-tests.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY

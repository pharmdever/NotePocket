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
adb shell settings put secure show_ime_with_hard_keyboard 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# The emulator launcher can ANR during first boot; stop only that unrelated process before UI tests.
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell am instrument -w -e notClass com.notepocket.app.UpdatePersistenceTest,com.notepocket.app.LegacyUpgradeSeedTest,com.notepocket.app.ReleaseUpgradeTest com.notepocket.notes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/device-tests.txt
python3 - <<'PY'
from pathlib import Path
text=Path('qa-output/device-tests.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY
# Raise the version code while keeping the package and key, then install without clearing data.
gradle assembleDebug -PnotePocketVersionCode=7
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e class com.notepocket.app.UpdatePersistenceTest com.notepocket.notes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/update-tests.txt
python3 - <<'PY'
from pathlib import Path
text=Path('qa-output/update-tests.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY

if [ -n "${NP_KEYSTORE:-}" ]; then
  adb install -r previous-release/app-release.apk
  adb install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
  adb shell am instrument -w -e class com.notepocket.app.LegacyUpgradeSeedTest com.notepocket.notes.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/legacy-release-seed.txt
  python3 - <<'PYSEED'
from pathlib import Path
text=Path('qa-output/legacy-release-seed.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PYSEED
  adb install -r app/build/outputs/apk/release/app-release.apk
  adb shell am instrument -w -e class com.notepocket.app.ReleaseUpgradeTest com.notepocket.notes.test/androidx.test.runner.AndroidJUnitRunner | tee qa-output/signed-release-upgrade.txt
  python3 - <<'PYRELEASE'
from pathlib import Path
text=Path('qa-output/signed-release-upgrade.txt').read_text()
assert 'OK (' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PYRELEASE
  "${ANDROID_HOME}/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk > qa-output/release-signature.txt
fi

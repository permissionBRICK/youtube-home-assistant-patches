#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk=${ANDROID_HOME:-/opt/android-sdk}
bt="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
adb="$sdk/platform-tools/adb"
test_dir=$(mktemp -d)
trap '"$adb" uninstall net.permissionbrick.ha.test >/dev/null 2>&1; rm -r "$test_dir"' EXIT
mkdir -p "$test_dir/res/values" "$test_dir/classes" "$test_dir/dex"
cat > "$test_dir/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="net.permissionbrick.ha.test">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="30"/>
<uses-permission android:name="android.permission.INTERNET"/>
<application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:usesCleartextTraffic="true">
<activity android:name="net.permissionbrick.ha.ButtonTestActivity" android:exported="true"/>
</application></manifest>
XML
cat > "$test_dir/res/values/strings.xml" <<'XML'
<resources><string name="accessibility_pause">Pause video</string><string name="accessibility_play">Play video</string></resources>
XML
"$bt/aapt2" compile --dir "$test_dir/res" -o "$test_dir/resources.zip"
"$bt/aapt2" link -I "$android" --manifest "$test_dir/AndroidManifest.xml" -o "$test_dir/test.apk" "$test_dir/resources.zip"
javac --release 17 -cp "$android" -d "$test_dir/classes" extensions/src/main/java/net/permissionbrick/ha/*.java tests/ButtonTestActivity.java
java -cp tools/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$android" --output "$test_dir/dex" "$test_dir"/classes/net/permissionbrick/ha/*.class
python3 - "$test_dir" <<'PY'
import sys, zipfile
from pathlib import Path
p = Path(sys.argv[1])
with zipfile.ZipFile(p / 'test.apk', 'a') as apk:
    apk.write(p / 'dex/classes.dex', 'classes.dex')
PY
"$bt/zipalign" 4 "$test_dir/test.apk" "$test_dir/aligned.apk"
keytool -genkeypair -keystore "$test_dir/key.jks" -storepass android -keypass android -alias test -dname CN=Test -keyalg RSA -validity 1 >/dev/null 2>&1
"$bt/apksigner" sign --ks "$test_dir/key.jks" --ks-pass pass:android "$test_dir/aligned.apk"
"$adb" install --no-incremental -r "$test_dir/aligned.apk" >/dev/null
"$adb" logcat -c
"$adb" shell am start -W -n net.permissionbrick.ha.test/net.permissionbrick.ha.ButtonTestActivity >/dev/null
for attempt in {1..20}; do
    "$adb" logcat -d -s HA-TEST:I AndroidRuntime:E > "$test_dir/result"
    if grep -q 'PASS:' "$test_dir/result"; then cat "$test_dir/result"; exit 0; fi
    if grep -q 'FATAL EXCEPTION' "$test_dir/result"; then cat "$test_dir/result"; exit 1; fi
    sleep 1
done
cat "$test_dir/result"
echo 'Android test timed out' >&2
exit 1

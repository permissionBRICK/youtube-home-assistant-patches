#!/usr/bin/env bash
# Install the Android platform needed to build; JDK 21 and Python 3 are prerequisites.
set -euo pipefail
sdk_root=${ANDROID_HOME:-/opt/android-sdk}
mkdir -p "$sdk_root"
exec 9>"$sdk_root/.setup.lock"
flock 9
if [[ ! -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]]; then
    setup_dir=$(mktemp -d)
    trap 'rm -r "$setup_dir"' EXIT
    curl --fail --location --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$setup_dir/tools.zip"
    unzip -q "$setup_dir/tools.zip" -d "$setup_dir"
    mkdir -p "$sdk_root/cmdline-tools/latest"
    cp -a "$setup_dir/cmdline-tools/." "$sdk_root/cmdline-tools/latest/"
fi
if [[ ! -f "$sdk_root/platforms/android-36/android.jar" ]]; then
    # sdkmanager reads a finite number of responses; avoid SIGPIPE from yes with pipefail.
    responses=$(mktemp)
    trap 'rm "$responses"; if [[ -n ${setup_dir:-} ]]; then rm -r "$setup_dir"; fi' EXIT
    for ((i=0; i<100; i++)); do printf 'y\n'; done > "$responses"
    "$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" --licenses < "$responses"
    "$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" 'platforms;android-36'
fi

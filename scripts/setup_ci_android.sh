#!/usr/bin/env bash
set -euo pipefail
# Only an isolated hosted runner executes this helper. No user machine setup.
test -n "${RUNNER_TEMP:-}"
test -n "${GITHUB_ENV:-}"
venith_sdk="$RUNNER_TEMP/venith-android-sdk"
mkdir -p "$venith_sdk/cmdline-tools" smoke-evidence
venith_archive="$RUNNER_TEMP/commandlinetools-linux-15859902_latest.zip"
curl --fail --location --retry 3 --max-time 240 --output "$venith_archive" 'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
printf '%s  %s\n' '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583' "$venith_archive" | sha256sum --check
unzip -q "$venith_archive" -d "$venith_sdk/cli-staging"
mv "$venith_sdk/cli-staging/cmdline-tools" "$venith_sdk/cmdline-tools/latest"
export JAVA_HOME="$JAVA_HOME_17_X64"
export ANDROID_HOME="$venith_sdk"
export ANDROID_SDK_ROOT="$venith_sdk"
export PATH="$JAVA_HOME/bin:$venith_sdk/cmdline-tools/latest/bin:$venith_sdk/platform-tools:$PATH"
printf 'JAVA_HOME=%s\nANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$JAVA_HOME" "$venith_sdk" "$venith_sdk" >> "$GITHUB_ENV"
printf '%s\n' "$JAVA_HOME/bin" "$venith_sdk/cmdline-tools/latest/bin" "$venith_sdk/platform-tools" >> "$GITHUB_PATH"
printf 'y\n%.0s' {1..100} | sdkmanager --licenses > smoke-evidence/sdk-licenses.log 2>&1
sdkmanager 'platform-tools' 'emulator' 'platforms;android-35' 'build-tools;35.0.0' > smoke-evidence/sdk-packages.log 2>&1
echo ANDROID_SDK_BOOTSTRAP_PASS

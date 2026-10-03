#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-evidence
export ANDROID_AVD_HOME="$RUNNER_TEMP/venith-avd"
export ANDROID_USER_HOME="$RUNNER_TEMP/venith-android-user"
mkdir -p "$ANDROID_AVD_HOME" "$ANDROID_USER_HOME"
sdkmanager 'system-images;android-35;google_apis;x86_64' > smoke-evidence/sdk-image.log 2>&1
# A generic AVD avoids an unavailable vendor device-profile XML in current tools.
mkdir -p "$ANDROID_AVD_HOME/venith-smoke.avd"
cat > "$ANDROID_AVD_HOME/venith-smoke.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/venith-smoke.avd
target=android-35
EOF
cat > "$ANDROID_AVD_HOME/venith-smoke.avd/config.ini" <<EOF
avd.ini.encoding=UTF-8
AvdId=venith-smoke
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
disk.dataPartition.size=2G
image.sysdir.1=$ANDROID_HOME/system-images/android-35/google_apis/x86_64/
tag.id=google_apis
tag.display=Google APIs
PlayStore.enabled=false
showDeviceFrame=no
EOF
"$ANDROID_HOME/emulator/emulator" -list-avds > smoke-evidence/avd.log
grep -qx venith-smoke smoke-evidence/avd.log
sudo chmod 666 /dev/kvm
"$ANDROID_HOME/emulator/emulator" -avd venith-smoke -port 5554 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -memory 2048 > smoke-evidence/emulator.log 2>&1 &
emulator_pid=$!
finish() {
  adb -s emulator-5554 emu kill >/dev/null 2>&1 || true
  if kill -0 "$emulator_pid" 2>/dev/null; then kill "$emulator_pid"; fi
}
trap finish EXIT
timeout 180 adb -s emulator-5554 wait-for-device
for n in $(seq 1 120); do
  if [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
test "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1
adb -s emulator-5554 shell input keyevent 82
adb -s emulator-5554 shell wm size 1080x1920
adb -s emulator-5554 shell wm density 420
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm grant com.venith.dictation android.permission.RECORD_AUDIO
adb -s emulator-5554 shell ime enable com.venith.dictation/com.voiceflowkeyboard.ime.VoiceFlowKeyboardService
adb -s emulator-5554 shell ime set com.venith.dictation/com.voiceflowkeyboard.ime.VoiceFlowKeyboardService
adb -s emulator-5554 shell am instrument -w -r com.venith.dictation.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-evidence/instrumentation.txt
grep -Eq 'OK \([0-9]+ tests?\)' smoke-evidence/instrumentation.txt
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' smoke-evidence/instrumentation.txt; then exit 1; fi
adb -s emulator-5554 pull /sdcard/Android/data/com.venith.dictation/files/verification smoke-evidence/ > smoke-evidence/pull.log 2>&1
adb -s emulator-5554 shell dumpsys meminfo com.venith.dictation > smoke-evidence/emulator-idle-memory.txt
echo EMULATOR_SMOKE_PASS

#!/usr/bin/env bash
# Builds the signed release of Caret Launcher: the App Bundle for Google Play, and with --apk also
# the APK to install on a device.
#
# It asks for the upload key (the keystore, its alias and passwords) and hands them to this one
# Gradle build in the environment, so they are not written to a file or the shell history. The
# build runs without the Gradle daemon and the configuration cache, which would keep them.
#
# Usage: scripts/build-release.sh [--apk]
set -euo pipefail
cd "$(dirname "$0")/.."

tasks=(:app:bundleRelease)
for arg in "$@"; do
  case "$arg" in
    --apk) tasks+=(:app:assembleRelease) ;;
    *) echo "Usage: $0 [--apk]" >&2; exit 2 ;;
  esac
done

read -r -p "Keystore file: " store_file
store_file="${store_file/#\~/$HOME}"
if [[ ! -f "$store_file" ]]; then
  echo "No such file: $store_file" >&2
  exit 1
fi
read -r -p "Key alias [upload]: " key_alias
read -r -s -p "Keystore password: " store_password; echo
read -r -s -p "Key password [the keystore password]: " key_password; echo

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
CARET_SIGNING_STORE_FILE="$store_file" \
CARET_SIGNING_STORE_PASSWORD="$store_password" \
CARET_SIGNING_KEY_ALIAS="${key_alias:-upload}" \
CARET_SIGNING_KEY_PASSWORD="${key_password:-$store_password}" \
  ./gradlew --no-daemon --no-configuration-cache "${tasks[@]}"

echo
echo "App Bundle: app/build/outputs/bundle/release/app-release.aab"
if [[ " ${tasks[*]} " == *" :app:assembleRelease "* ]]; then
  echo "APK:        app/build/outputs/apk/release/app-release.apk"
fi

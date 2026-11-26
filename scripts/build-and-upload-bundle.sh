#!/usr/bin/env bash
# Builds the signed release App Bundle with scripts/build-release.sh and uploads it to Google Play
# without putting it on a track: it goes into the app bundle explorer, and a release in Play Console
# adds it from the library. The bundle's native libraries go up with it as its native debug symbols
# (see UploadBundleTask in app/build.gradle.kts).
#
# It asks first for the Google Play service account's key (a JSON file kept outside the repository),
# then scripts/build-release.sh asks for the upload key. The service account key goes to the upload
# build only, in ANDROID_PUBLISHER_CREDENTIALS, without the Gradle daemon and the configuration cache,
# which would keep it.
#
# Usage: scripts/build-and-upload-bundle.sh
set -euo pipefail
cd "$(dirname "$0")/.."

read -r -p "Service account key (JSON): " key_file
key_file="${key_file/#\~/$HOME}"
if [[ ! -f "$key_file" ]]; then
  echo "No such file: $key_file" >&2
  exit 1
fi

scripts/build-release.sh

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ANDROID_PUBLISHER_CREDENTIALS="$(cat "$key_file")" \
  ./gradlew --no-daemon --no-configuration-cache :app:uploadReleaseBundle

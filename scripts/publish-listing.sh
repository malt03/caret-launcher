#!/usr/bin/env bash
# Publishes Caret Launcher's Google Play store listing (app/src/main/play: the texts, icon, feature
# graphic and phone and 10-inch tablet screenshots of each language) with Gradle Play Publisher.
#
# It asks for the Google Play service account's key (a JSON file kept outside the repository) and
# hands its contents to this one Gradle build in ANDROID_PUBLISHER_CREDENTIALS, which the plugin
# reads. The build runs without the Gradle daemon and the configuration cache, which would keep it.
# It reruns the tasks: otherwise Gradle skips the files it sent last time, even when Google Play
# refused to commit that edit. Play keeps the images it already has with the same contents.
#
# Usage: scripts/publish-listing.sh
set -euo pipefail
cd "$(dirname "$0")/.."

read -r -p "Service account key (JSON): " key_file
key_file="${key_file/#\~/$HOME}"
if [[ ! -f "$key_file" ]]; then
  echo "No such file: $key_file" >&2
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ANDROID_PUBLISHER_CREDENTIALS="$(cat "$key_file")" \
  ./gradlew --no-daemon --no-configuration-cache :app:publishListing --rerun-tasks

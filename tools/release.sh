#!/usr/bin/env bash
# Build, verify and (optionally) publish a Tap Zoo release to GitHub Releases, where Parent settings › App updates finds it.
#
#   tools/release.sh <versionCode> <versionName>            # build + verify + print the gh command (dry run)
#   tools/release.sh <versionCode> <versionName> --publish  # ...and run `gh release create` (needs Peyton's OK)
#
# Naming convention (the app depends on it, see RELEASING.md):
#   tag  v<versionName>                               e.g. v9
#   one asset  tap-zoo-v<versionName>-vc<versionCode>.apk   e.g. tap-zoo-v9-vc2.apk
# Tap Zoo ships the debug build (signed with ~/.android/debug.keystore), and it must include the private sounds submodule.
set -euo pipefail
cd "$(dirname "$0")/.."
VC="${1:?versionCode}"; VN="${2:?versionName}"; PUBLISH="${3:-}"
CERT=65f0d4cb5041d1d60f2fbbb44d0bc73174de9ea1668be0038129630a6a4d113f
PKG=com.peyton.toddlergame; REPO=PeytonRingling/tap-zoo
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/box/android-sdk}}"; BT="$SDK/build-tools/34.0.0"
TAG="v$VN"; NAME="tap-zoo-v$VN-vc$VC.apk"; OUT="release/$NAME"

[[ "$VC" =~ ^[0-9]+$ ]] || { echo "versionCode must be a number" >&2; exit 1; }
[[ "$VN" =~ ^[0-9][0-9A-Za-z.]*$ ]] || { echo "versionName must look like 9" >&2; exit 1; }

git submodule update --init sounds
./gradlew -q assembleDebug -Pvc="$VC" -Pvn="$VN"
mkdir -p release && cp app/build/outputs/apk/debug/app-debug.apk "$OUT"

badging=$("$BT/aapt" dump badging "$OUT" | sed -n 1p)  # not head: with pipefail, head's early exit SIGPIPEs aapt
echo "$badging"
[[ "$badging" == *"name='$PKG' versionCode='$VC' versionName='$VN'"* ]] || { echo "FAIL: package/version mismatch" >&2; exit 1; }
certs=$("$BT/apksigner" verify --print-certs "$OUT" | grep -o 'SHA-256 digest: [0-9a-f]*' | awk '{print $3}' | sort -u)
echo "signer: $certs"
[[ "$certs" == "$CERT" ]] || { echo "FAIL: not signed with the release key (see RELEASING.md)" >&2; exit 1; }
sounds=$(unzip -l "$OUT" | grep -c 'res/raw/animal_' || true)
echo "animal sounds: $sounds"
[[ "$sounds" == 8 ]] || { echo "FAIL: expected 8 res/raw/animal_* sounds (is the sounds submodule checked out?)" >&2; exit 1; }

# Music + balloon pop are in the public repo (res/raw); make sure they made it into the APK.
listing=$(unzip -l "$OUT")
for f in music_balloons.ogg music_bubbles.ogg music_menu.ogg music_lullaby.ogg pop_balloon.wav; do
  grep -q "res/raw/$f\$" <<<"$listing" || { echo "FAIL: res/raw/$f missing from the APK" >&2; exit 1; }
done
echo "music + pop: packaged"

latest=$(gh release view --repo "$REPO" --json assets -q '.assets[].name' 2>/dev/null | sed -n 's/.*-vc\([0-9]*\)\.apk$/\1/p' | sort -n | tail -1 || true)
if [[ -n "$latest" && "$VC" -le "$latest" ]]; then echo "FAIL: versionCode $VC is not above the latest release ($latest)" >&2; exit 1; fi

CMD=(gh release create "$TAG" "$OUT" --repo "$REPO" --target main --title "Tap Zoo v$VN" --notes "Tap Zoo v$VN (build $VC). Install on the phone: hold the gear 3 s › App updates › Install.")
echo "OK: $OUT"
if [[ "$PUBLISH" == "--publish" ]]; then
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "FAIL: HEAD isn't origin/main; merge and pull first" >&2; exit 1; }
  "${CMD[@]}"
else
  printf 'Dry run. To publish:\n  '; printf '%q ' "${CMD[@]}"; echo
fi

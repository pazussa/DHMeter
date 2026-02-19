#!/usr/bin/env bash
set -euo pipefail

# Downloads the latest testing APK from GitHub Releases and sends it to an Android phone
# over Wi-Fi ADB, leaving it in /sdcard/Download ready to install.

REPO_DEFAULT="pazussa/DHMeter"
RELEASE_TAG_DEFAULT="testing-latest"
ASSET_NAME_DEFAULT="dropindh-testing-latest.apk"
PHONE_DEST_DEFAULT="/sdcard/Download/dropindh-testing-latest.apk"

usage() {
  cat <<USAGE
Usage:
  $(basename "$0") --device <ip:port> [options]
  $(basename "$0") --auto [options]

Required (one of):
  --device <ip:port>    Android device endpoint (e.g. 192.168.1.25:5555)
  --auto                Use first online device from 'adb devices'

Options:
  --pair <ip:port>      Pair endpoint from Android Wireless Debugging (optional)
  --pair-code <code>    Pairing code shown on phone (optional)
  --repo <owner/name>   GitHub repo (default: ${REPO_DEFAULT})
  --tag <tag>           Release tag (default: ${RELEASE_TAG_DEFAULT})
  --asset <name>        Release asset filename (default: ${ASSET_NAME_DEFAULT})
  --dest <path>         Destination path on phone (default: ${PHONE_DEST_DEFAULT})
  --open-installer      Try to open package installer after pushing APK
  --install             Install directly using adb install -r (no manual tap)
  --keep-local          Keep downloaded APK in ./tmp-apk
  -h, --help            Show this help

Examples:
  $(basename "$0") --pair 192.168.1.25:37001 --pair-code 123456 --device 192.168.1.25:5555 --open-installer
  $(basename "$0") --device 192.168.1.25:5555 --open-installer
  $(basename "$0") --auto --install
USAGE
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: command not found: $cmd" >&2
    exit 1
  fi
}

DEVICE=""
AUTO_DEVICE=false
PAIR_ENDPOINT=""
PAIR_CODE=""
REPO="$REPO_DEFAULT"
TAG="$RELEASE_TAG_DEFAULT"
ASSET_NAME="$ASSET_NAME_DEFAULT"
PHONE_DEST="$PHONE_DEST_DEFAULT"
OPEN_INSTALLER=false
INSTALL_DIRECT=false
KEEP_LOCAL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE="${2:-}"
      shift 2
      ;;
    --auto)
      AUTO_DEVICE=true
      shift
      ;;
    --pair)
      PAIR_ENDPOINT="${2:-}"
      shift 2
      ;;
    --pair-code)
      PAIR_CODE="${2:-}"
      shift 2
      ;;
    --repo)
      REPO="${2:-}"
      shift 2
      ;;
    --tag)
      TAG="${2:-}"
      shift 2
      ;;
    --asset)
      ASSET_NAME="${2:-}"
      shift 2
      ;;
    --dest)
      PHONE_DEST="${2:-}"
      shift 2
      ;;
    --open-installer)
      OPEN_INSTALLER=true
      shift
      ;;
    --install)
      INSTALL_DIRECT=true
      shift
      ;;
    --keep-local)
      KEEP_LOCAL=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$DEVICE" && "$AUTO_DEVICE" = false ]]; then
  echo "Error: you must pass --device <ip:port> or --auto" >&2
  usage
  exit 1
fi

if [[ -n "$PAIR_ENDPOINT" && -z "$PAIR_CODE" ]]; then
  echo "Error: --pair requires --pair-code" >&2
  exit 1
fi

require_cmd gh
require_cmd adb
require_cmd curl

discover_mdns_connect_endpoint() {
  local host_filter="${1:-}"
  local discovered
  discovered="$(
    adb mdns services 2>/dev/null | awk -v host="$host_filter" '
      $2 == "_adb-tls-connect._tcp" {
        endpoint = $3
        if (host == "") {
          print endpoint
          exit
        }
        split(endpoint, parts, ":")
        if (parts[1] == host) {
          print endpoint
          exit
        }
      }
    '
  )"
  printf '%s' "$discovered"
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$ROOT_DIR/tmp-apk"
mkdir -p "$TMP_DIR"
LOCAL_APK="$TMP_DIR/$ASSET_NAME"

echo "[1/5] Downloading latest APK from release '$TAG' in '$REPO'..."
ASSET_URL="https://github.com/${REPO}/releases/download/${TAG}/${ASSET_NAME}"
curl -fsSL "$ASSET_URL" -o "$LOCAL_APK"
if [[ ! -s "$LOCAL_APK" ]]; then
  echo "Error: failed to download APK from $ASSET_URL" >&2
  exit 1
fi

echo "[2/5] Resolving Android device..."
PAIR_HOST=""
if [[ -n "$PAIR_ENDPOINT" ]]; then
  echo "Pairing with device at $PAIR_ENDPOINT..."
  PAIR_HOST="${PAIR_ENDPOINT%%:*}"
  if pair_output="$(printf '%s\n' "$PAIR_CODE" | adb pair "$PAIR_ENDPOINT" 2>&1)"; then
    echo "$pair_output"
  else
    echo "Warning: adb pair failed. Continuing with adb connect attempt..."
    echo "adb pair output: $pair_output"
    echo "Tip: pairing code expires quickly; generate a fresh code on the phone and retry."
  fi
fi

if [[ -n "$DEVICE" ]]; then
  adb connect "$DEVICE" >/dev/null || true
fi

if [[ -n "$PAIR_ENDPOINT" && -n "$DEVICE" ]]; then
  DEVICE_STATE_TMP="$(adb -s "$DEVICE" get-state 2>/dev/null || true)"
  if [[ "$DEVICE_STATE_TMP" != "device" ]]; then
    MDNS_ENDPOINT="$(discover_mdns_connect_endpoint "$PAIR_HOST")"
    if [[ -n "$MDNS_ENDPOINT" && "$MDNS_ENDPOINT" != "$DEVICE" ]]; then
      echo "Using discovered connect endpoint from mDNS: $MDNS_ENDPOINT"
      DEVICE="$MDNS_ENDPOINT"
      adb connect "$DEVICE" >/dev/null || true
    fi
  fi
fi

if [[ "$AUTO_DEVICE" = true ]]; then
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [[ -z "$DEVICE" ]]; then
    echo "Error: no online adb device found. Use --device <ip:port> or connect one first." >&2
    exit 1
  fi
fi

DEVICE_STATE="$(adb -s "$DEVICE" get-state 2>/dev/null || true)"
if [[ "$DEVICE_STATE" != "device" ]]; then
  # If there is exactly one online adb device, use it as fallback.
  mapfile -t ONLINE_DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ "${#ONLINE_DEVICES[@]}" -eq 1 ]]; then
    echo "Device '$DEVICE' is not online; using only online adb device: ${ONLINE_DEVICES[0]}"
    DEVICE="${ONLINE_DEVICES[0]}"
    DEVICE_STATE="$(adb -s "$DEVICE" get-state 2>/dev/null || true)"
  fi
fi

if [[ "$DEVICE_STATE" != "device" ]]; then
  echo "Error: device '$DEVICE' is not online in adb."
  echo "adb devices output:"
  adb devices || true
  if adb mdns services >/dev/null 2>&1; then
    echo "adb mdns services output (possible endpoints):"
    adb mdns services || true
  fi
  echo "Tip: enable Wireless debugging on phone and use fresh endpoints:"
  echo "  1) adb pair <ip:pair_port>"
  echo "  2) adb connect <ip:connect_port>"
  exit 1
fi

echo "[3/5] Pushing APK to phone: $PHONE_DEST"
adb -s "$DEVICE" push "$LOCAL_APK" "$PHONE_DEST" >/dev/null

echo "[4/5] Verifying file on phone..."
PHONE_SIZE="$(adb -s "$DEVICE" shell "stat -c %s '$PHONE_DEST'" 2>/dev/null | tr -d '\r' || true)"
LOCAL_SIZE="$(stat -c %s "$LOCAL_APK")"
if [[ "$PHONE_SIZE" != "$LOCAL_SIZE" ]]; then
  echo "Warning: size mismatch (local=$LOCAL_SIZE, phone=$PHONE_SIZE)."
else
  echo "OK: APK copied correctly (${LOCAL_SIZE} bytes)."
fi

if [[ "$INSTALL_DIRECT" = true ]]; then
  echo "[5/5] Installing directly with adb..."
  adb -s "$DEVICE" install -r "$LOCAL_APK"
  echo "Done: APK installed."
elif [[ "$OPEN_INSTALLER" = true ]]; then
  echo "[5/5] Opening installer intent on phone..."
  # Use INSTALL_PACKAGE (not generic VIEW) to avoid unrelated apps handling .apk files.
  if adb -s "$DEVICE" shell am start \
    -a android.intent.action.INSTALL_PACKAGE \
    -d "file://$PHONE_DEST" \
    -t "application/vnd.android.package-archive" \
    --ez android.intent.extra.NOT_UNKNOWN_SOURCE true \
    --grant-read-uri-permission >/dev/null 2>&1; then
    echo "Done: APK copied and package installer intent sent."
  else
    echo "Warning: could not open package installer intent. Falling back to adb install -r..."
    adb -s "$DEVICE" install -r "$LOCAL_APK"
    echo "Done: APK installed with adb."
  fi
else
  echo "[5/5] Done: APK copied to phone and ready to install manually."
  echo "Path on phone: $PHONE_DEST"
fi

if [[ "$KEEP_LOCAL" = false ]]; then
  rm -f "$LOCAL_APK"
fi

echo "Direct download URL used: $ASSET_URL"

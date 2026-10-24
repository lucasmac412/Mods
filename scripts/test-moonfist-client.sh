#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
moonfist_display="${MOONFIST_TEST_DISPLAY:-:93}"
moonfist_xvfb="${MOONFIST_XVFB:-Xvfb}"
"$moonfist_xvfb" "$moonfist_display" -screen 0 1280x720x24 -nolisten tcp > moonfist-mod/build/xvfb.log 2>&1 &
moonfist_xvfb_pid=$!
trap 'kill "$moonfist_xvfb_pid" 2>/dev/null || true' EXIT
DISPLAY="$moonfist_display" LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null ./gradlew --no-daemon -p moonfist-mod runClientGameTest "$@"

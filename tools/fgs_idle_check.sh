#!/usr/bin/env bash
# Foreground-service contract check for MediaService (the "did not then call Service.startForeground()" ANR,
# top ANR in production up to v328).
#
#   tools/fgs_idle_check.sh <adb-serial> [package]      default package: com.driot.bookplayerfull.debug
#   WAIT=12 tools/fgs_idle_check.sh ...                  short run: leftover notifications only
#
# For every command MediaService accepts, the app is restarted (service idle: bound, not in foreground),
# sent to the background, and the command is delivered with startForegroundService(), as the app, a media
# button or another app would. Android reports the ANR 10-40 s after a command that never called
# startForeground(), hence the default 48 s wait per command (about 15 minutes in total).
# Expected on every line: anr=0 didNotCallStartForeground=0 fatal=0 leftoverNotification=[]
# (CMD_PLAY legitimately resumes the last book and shows its notification).
A="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"; S=${1:?usage: $0 <adb-serial> [package]}
P=${2:-com.driot.bookplayerfull.debug}; SVC=$P/com.driot.bookplayer.player.MediaService
CMDS=("CMD_STOP" "CMD_PAUSE|fg" "CMD_NEXT" "CMD_PREV" "CMD_SEEK" "com.driot.bookplayer.CMD_PREPARE_RESTORED"
      "CMD_UPDATE_SLEEP|fg" "CMD_RESET_LAST_USER_ACTION|fg" "CMD_SET_SPEED|fg" "CMD_TTS_SET_START|fg"
      "CMD_TTS_GET_TEXT|fg" "com.driot.bookplayer.ACTION_PODCAST_DOWNLOAD_COMPLETED|fg" "SOME_UNKNOWN_ACTION"
      "CMD_PLAY|fg" "android.intent.action.MEDIA_BUTTON")
for c in "${CMDS[@]}"; do
  act=${c%%|*}; fg=""; [[ "$c" == *"|fg" ]] && fg="--ez EXTRA_FOREGROUND true"
  $A -s $S shell am force-stop $P; sleep 1
  $A -s $S shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 7
  $A -s $S shell input keyevent KEYCODE_HOME; sleep 1
  $A -s $S logcat -c
  $A -s $S shell am start-foreground-service -n $SVC -a "$act" $fg >/dev/null 2>&1
  sleep ${WAIT:-48}
  L=$($A -s $S logcat -d)
  anr=$(echo "$L" | grep -c "ANR in $P"); fatal=$(echo "$L" | grep -A2 "FATAL EXCEPTION" | grep -c "$P")
  didnot=$(echo "$L" | grep -c "did not then call")
  notif=$($A -s $S shell dumpsys notification --noredact 2>/dev/null | grep -A40 "NotificationRecord.*pkg=$P " | grep -m1 "android.title=" | sed 's/.*android.title=//' | cut -c1-50)
  echo "$act ${fg:+(fg)} : anr=$anr didNotCallStartForeground=$didnot fatal=$fatal leftoverNotification=[${notif}]"
done
$A -s $S shell input keyevent KEYCODE_MEDIA_STOP

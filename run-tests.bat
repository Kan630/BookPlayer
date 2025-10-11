@echo off
echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo Cleaning test state...
adb uninstall com.driot.bookplayer.debug 2>nul
adb uninstall com.driot.bookplayer.debug.test 2>nul

echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo Building APKs...
call gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo Installing APKs...
call gradlew.bat :app:installDebug :app:installDebugAndroidTest
if errorlevel 1 (
    echo Install failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo hard boot adb
call C:\Users\adrio\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am instrument -w -r -e class com.driot.bookplayer.test.JustOpenAndWait com.driot.bookplayer.debug.test/androidx.test.runner.AndroidJUnitRunner

echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo hard boot adb 2
call C:\Users\adrio\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am instrument -w -r -e class com.driot.bookplayer.test.JustOpenAndWait com.driot.bookplayer.debug.test/androidx.test.runner.AndroidJUnitRunner

echo ------------------------------------------------------------------------------
echo ------------------------------------------------------------------------------
echo Running tests...
call gradlew.bat :app:connectedDebugAndroidTest


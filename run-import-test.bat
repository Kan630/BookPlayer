@echo off
REM Run ImportBookTest (or other instrumented tests) outside IDE.
REM
REM Usage:
REM   run-import-test.bat                       - run ImportBookTest (default MODE=test)
REM   run-import-test.bat build                 - run ImportBookTest with MODE=build
REM   run-import-test.bat <serial>              - run ImportBookTest on device (e.g. FPMPH18C17900002)
REM   run-import-test.bat <serial> keep         - same, keep app, no uninstall
REM   run-import-test.bat ImportBookTest <serial> - run that test class on device
REM   run-import-test.bat ImportBookTest <serial> keep
REM
REM Ensure a device/emulator is connected: adb devices
REM
REM Samsung SM-A165F - 16 : RF8Y50GB1XP
REM Huawei LLD-L31 - 9 : FPMPH18C17900002
REM
REM cd StudioProjects\BookPlayer
REM run-import-test.bat ImportBookTest FPMPH18C17900002 keep

setlocal EnableExtensions DisableDelayedExpansion
set START_TIME=%DATE% %TIME%

set TEST_CLASS=com.driot.bookplayer.test.ImportBookTest
set EXTRA_ARGS=
set DEVICE_ARG=
set DEVICE_SERIAL=
set KEEP_APP=0

if "%~1"=="build" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=build
    if not "%~2"=="" (
        set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
        set DEVICE_SERIAL=%~2
    )
) else if "%~1"=="test" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=test
    if not "%~2"=="" (
        set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
        set DEVICE_SERIAL=%~2
    )
) else if not "%~1"=="" (
    REM Arg1: test class (e.g. ImportBookTest) or device serial (e.g. FPMPH18C17900002)
    REM Serials are typically uppercase+digits; class names have lowercase (PascalCase)
    echo %~1| findstr /r "[a-z]" >nul
    if not errorlevel 1 (
        REM Has lowercase - assume test class
        set TEST_CLASS=com.driot.bookplayer.test.%~1
        if not "%~2"=="" (
            set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
            set DEVICE_SERIAL=%~2
        )
    ) else (
        REM Uppercase/digits only - assume device serial
        set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~1
        set DEVICE_SERIAL=%~1
    )
)
if "%~2"=="keep" set KEEP_APP=1
if "%~3"=="keep" set KEEP_APP=1

echo ------------------------------------------------------------------------------
echo Running: %TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
echo ------------------------------------------------------------------------------
if %KEEP_APP%==0 (
    echo Cleaning test state...
    if not "%DEVICE_SERIAL%"=="" (
        adb -s %DEVICE_SERIAL% uninstall com.driot.bookplayer.debug 2>nul
        adb -s %DEVICE_SERIAL% uninstall com.driot.bookplayer.debug.test 2>nul
    ) else (
        adb uninstall com.driot.bookplayer.debug 2>nul
        adb uninstall com.driot.bookplayer.debug.test 2>nul
    )
) else (
    echo Keeping app installs - no uninstall
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Building APKs...
call gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Installing APKs...
if not "%DEVICE_SERIAL%"=="" (
    if not exist "app\build\outputs\apk\debug\app-debug.apk" (
        echo Missing app APK: app\build\outputs\apk\debug\app-debug.apk
        exit /b 1
    )
    if not exist "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk" (
        echo Missing androidTest APK: app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
        exit /b 1
    )
    for /f "tokens=1 delims=." %%x in ("%TIME%") do set APP_START=%%x
    echo Installing app APK on %DEVICE_SERIAL% ...
    adb -s %DEVICE_SERIAL% install -r -g "app\build\outputs\apk\debug\app-debug.apk"
    if errorlevel 1 (
        echo Install failed - app APK !
        exit /b 1
    )
    echo App APK install finished at %TIME% (duration: %APP_START% -> %TIME%)
    for /f "tokens=1 delims=." %%x in ("%TIME%") do set TEST_START=%%x
    echo Installing androidTest APK on %DEVICE_SERIAL% ...
    adb -s %DEVICE_SERIAL% install -r -g "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if errorlevel 1 (
        echo Install failed - androidTest APK !
        exit /b 1
    )
    echo androidTest APK install finished at %TIME% (duration: %TEST_START% -> %TIME%)
    echo Verifying test package install...
    adb -s %DEVICE_SERIAL% shell pm path com.driot.bookplayer.debug.test
) else (
    call gradlew.bat :app:installDebug :app:installDebugAndroidTest
    if errorlevel 1 (
        echo Install failed!
        exit /b 1
    )
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Running test...
if not "%DEVICE_SERIAL%"=="" (
    set ANDROID_SERIAL=%DEVICE_SERIAL%
    echo Using device %DEVICE_SERIAL% only - skipping Gradle install tasks
    call gradlew.bat :app:connectedDebugAndroidTest -x :app:installDebug -x :app:installDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
) else (
    call gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Done. Started at %START_TIME%
echo ------------------------------------------------------------------------------
exit /b %ERRORLEVEL%

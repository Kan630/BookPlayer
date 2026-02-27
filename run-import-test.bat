@echo off
REM Run instrumented tests outside IDE (supports flavors and push/regular install modes)
REM
REM Usage examples:
REM   run-import-test.bat DeepSettingsTest 52003931eec16435 keep
REM   run-import-test.bat LoadManyBookTest push
REM   run-import-test.bat ImportBookTest 52003931eec16435 push keep
REM   run-import-test.bat ImportBookTest 52003931eec16435 push keep extraarg
REM
REM Flavors: Full (default), Legacy, Pure - change FLAVOR below if needed
REM
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set FLAVOR=Full
set FLAVOR_LOWER=full
set BUILD_VARIANT=%FLAVOR%Debug
set START_TIME=%DATE% %TIME%
echo ------------------------------------------------------------------------------
echo Arguments received:
echo Arg1: %~1
echo Arg2: %~2
echo Arg3: %~3
echo Arg4: %~4
echo Total arguments: %*
echo ------------------------------------------------------------------------------
set TEST_CLASS=com.driot.bookplayer.test.ImportBookTest
set EXTRA_ARGS=
set DEVICE_ARG=
set DEVICE_SERIAL=
set KEEP_APP=0
set PUSH_MODE=0

REM ────────────────────────────────────────────────
REM Parse arguments - scan all args for flags
REM ────────────────────────────────────────────────
set ARG_INDEX=1
:parse_loop
set CURRENT_ARG=
for /f "tokens=%ARG_INDEX%*" %%a in ("%*") do set CURRENT_ARG=%%a
if "!CURRENT_ARG!"=="" goto parse_done

REM Check for named modes first
if /i "!CURRENT_ARG!"=="keep" (
    set KEEP_APP=1
    goto next_arg
)
if /i "!CURRENT_ARG!"=="push" (
    set PUSH_MODE=1
    goto next_arg
)
if /i "!CURRENT_ARG!"=="build" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=build
    goto next_arg
)
if /i "!CURRENT_ARG!"=="test" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=test
    goto next_arg
)

REM Check if it looks like a test class name
echo "!CURRENT_ARG!" | findstr /i "Test" >nul
if not errorlevel 1 (
    set TEST_CLASS=com.driot.bookplayer.test.!CURRENT_ARG!
    goto next_arg
)

REM Otherwise assume it's a device serial
set DEVICE_SERIAL=!CURRENT_ARG!
set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=!CURRENT_ARG!

:next_arg
set /a ARG_INDEX+=1
if !ARG_INDEX! LEQ 9 goto parse_loop

:parse_done
echo ------------------------------------------------------------------------------
echo Running: %TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
echo Using flavor: %FLAVOR% (%BUILD_VARIANT%)
echo Push mode: %PUSH_MODE%   Keep app: %KEEP_APP%
echo Device serial: %DEVICE_SERIAL%
echo ------------------------------------------------------------------------------

REM ────────────────────────────────────────────────
REM Uninstall if not keeping
REM ────────────────────────────────────────────────
if %KEEP_APP%==0 (
    echo Cleaning test state...
    if not "%DEVICE_SERIAL%"=="" (
        adb -s "%DEVICE_SERIAL%" uninstall com.driot.bookplayer.debug 2>nul
        adb -s "%DEVICE_SERIAL%" uninstall com.driot.bookplayer.debug.test 2>nul
    ) else (
        adb uninstall com.driot.bookplayer.debug 2>nul
        adb uninstall com.driot.bookplayer.debug.test 2>nul
    )
) else (
    echo Keeping app installs - no uninstall
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Building APKs...
call gradlew.bat :app:assemble%BUILD_VARIANT% :app:assemble%BUILD_VARIANT%AndroidTest
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Verifying APK files were built...
set "APK_APP_PATH=%CD%\app\build\outputs\apk\%FLAVOR_LOWER%\debug\app-%FLAVOR_LOWER%-debug.apk"
set "APK_TEST_PATH=%CD%\app\build\outputs\apk\androidTest\%FLAVOR_LOWER%\debug\app-%FLAVOR_LOWER%-debug-androidTest.apk"
if exist "%APK_APP_PATH%" (
    echo App APK found: %APK_APP_PATH%
    dir "%APK_APP_PATH%" | findstr /C:"app-%FLAVOR_LOWER%-debug.apk"
) else (
    echo ERROR: App APK not found: %APK_APP_PATH%
    if exist "%CD%\app\build\outputs\apk\%FLAVOR_LOWER%\debug\" dir "%CD%\app\build\outputs\apk\%FLAVOR_LOWER%\debug\"
    exit /b 1
)
if exist "%APK_TEST_PATH%" (
    echo Test APK found: %APK_TEST_PATH%
    dir "%APK_TEST_PATH%" | findstr /C:"app-%FLAVOR_LOWER%-debug-androidTest.apk"
    echo WARNING: Check size above - if over 1GB, this is abnormally large!
) else (
    echo ERROR: Test APK not found: %APK_TEST_PATH%
    if exist "%CD%\app\build\outputs\apk\androidTest\%FLAVOR_LOWER%\debug\" dir "%CD%\app\build\outputs\apk\androidTest\%FLAVOR_LOWER%\debug\"
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Installing APKs...

if %PUSH_MODE%==1 (
    echo Using PUSH install mode (manual adb install^)...
    if "!DEVICE_SERIAL!"=="" (
        set _COUNT=0
        for /f "tokens=1" %%i in ('adb devices ^| findstr /r /c:"device$"') do (
            echo %%i | findstr /i "List" >nul
            if errorlevel 1 (
                set /a _COUNT+=1
                if !_COUNT! EQU 1 set DEVICE_SERIAL=%%i
            )
        )
        if !_COUNT!==0 (
            echo ERROR: No device found for push install. Connect one and run 'adb devices'.
            exit /b 1
        )
        if !_COUNT! GTR 1 (
            echo ERROR: Multiple devices connected - specify serial when using push mode.
            exit /b 1
        )
        echo Auto-detected single device: !DEVICE_SERIAL!
    )
	echo Disabling Play Protect scan prompt...
	adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 0
    adb -s "!DEVICE_SERIAL!" install -r -g --no-streaming "%APK_APP_PATH%"
    if errorlevel 1 (
        echo Push install failed - app APK
        exit /b 1
    )
    adb -s "!DEVICE_SERIAL!" install -r -g --no-streaming "%APK_TEST_PATH%"
    if errorlevel 1 (
        echo Push install failed - androidTest APK
        exit /b 1
    )
    echo Push install completed.
) else if not "!DEVICE_SERIAL!"=="" (
    echo Using regular manual install on specified device !DEVICE_SERIAL!...
    echo Checking device storage...
    adb -s "!DEVICE_SERIAL!" shell df /data 2>nul | findstr /C:"/data"
    echo Cleaning up ADB temp files...
    adb -s "!DEVICE_SERIAL!" shell rm -rf /data/local/tmp/*.apk 2>nul
    adb -s "!DEVICE_SERIAL!" shell rm -rf /sdcard/Download/*.apk 2>nul
	echo Disabling Play Protect scan prompt...
	adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 0
    echo Installing app APK...
    adb -s "!DEVICE_SERIAL!" install -r -g --no-streaming "%APK_APP_PATH%"
    if errorlevel 1 (
        echo Install failed - app APK
        echo Path was: %APK_APP_PATH%
        exit /b 1
    )
    echo App APK installed.
    echo Installing androidTest APK...
    adb -s "!DEVICE_SERIAL!" install -r -g --no-streaming "%APK_TEST_PATH%"
    if errorlevel 1 (
        echo Install failed - androidTest APK
        echo Path was: %APK_TEST_PATH%
        exit /b 1
    )
    echo androidTest APK installed.
    echo Verifying test package...
    adb -s "!DEVICE_SERIAL!" shell pm path com.driot.bookplayer.debug.test
) else (
    echo No serial - using Gradle install tasks...
    call gradlew.bat :app:install%BUILD_VARIANT% :app:install%BUILD_VARIANT%AndroidTest
    if errorlevel 1 (
        echo Gradle install failed!
        exit /b 1
    )
)

echo ------------------------------------------------------------------------------
echo [%TIME%] Running test...

set GRADLE_CMD=call gradlew.bat :app:connected%BUILD_VARIANT%AndroidTest
set GRADLE_CMD=!GRADLE_CMD! -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS%

if not "!EXTRA_ARGS!"=="" set GRADLE_CMD=!GRADLE_CMD! !EXTRA_ARGS!
if not "!DEVICE_ARG!"=="" set GRADLE_CMD=!GRADLE_CMD! !DEVICE_ARG!

if not "!DEVICE_SERIAL!"=="" (
    echo Using device !DEVICE_SERIAL! - skipping Gradle install tasks
    set ANDROID_SERIAL=!DEVICE_SERIAL!
    set GRADLE_CMD=!GRADLE_CMD! -x :app:install%BUILD_VARIANT% -x :app:install%BUILD_VARIANT%AndroidTest
) else if %PUSH_MODE%==1 (
    echo Push mode: running on assumed single connected device...
    set ANDROID_SERIAL=!DEVICE_SERIAL!
    set GRADLE_CMD=!GRADLE_CMD! -x :app:install%BUILD_VARIANT% -x :app:install%BUILD_VARIANT%AndroidTest
)

echo Command: !GRADLE_CMD!
!GRADLE_CMD!

echo ------------------------------------------------------------------------------
echo [%TIME%] Done. Started at %START_TIME%
echo ------------------------------------------------------------------------------
exit /b %ERRORLEVEL%
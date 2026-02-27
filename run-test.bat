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

REM Enable ANSI color support (Windows 10+)
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1
for /f %%a in ('echo prompt $E^| cmd /q') do set "ESC=%%a"
set "BLUE=!ESC![94m"
set "ORANGE=!ESC![33m"
set "RESET=!ESC![0m"

set FLAVOR=Full
set FLAVOR_LOWER=full
set BUILD_VARIANT=%FLAVOR%Debug
set START_TIME=%TIME%

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo Arguments received:
echo Arg1: %~1
echo Arg2: %~2
echo Arg3: %~3
echo Arg4: %~4
echo Total arguments: %*
echo !BLUE!------------------------------------------------------------------------------!RESET!

set TEST_CLASS=com.driot.bookplayer.test.ImportBookTest
set EXTRA_ARGS=
set DEVICE_ARG=
set DEVICE_SERIAL=
set KEEP_APP=0
set PUSH_MODE=0

REM ────────────────────────────────────────────────
REM Parse arguments - scan all args for flags
REM ────────────────────────────────────────────────
:parse_loop
if "%~1"=="" goto parse_done

set CURRENT_ARG=%~1

if /i "!CURRENT_ARG!"=="keep" (
    set KEEP_APP=1
    shift & goto parse_loop
)
if /i "!CURRENT_ARG!"=="push" (
    set PUSH_MODE=1
    shift & goto parse_loop
)
if /i "!CURRENT_ARG!"=="build" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=build
    shift & goto parse_loop
)
if /i "!CURRENT_ARG!"=="test" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=test
    shift & goto parse_loop
)

REM Check if it looks like a test class name
echo "!CURRENT_ARG!" | findstr /i "Test" >nul
if not errorlevel 1 (
    set TEST_CLASS=com.driot.bookplayer.test.!CURRENT_ARG!
    shift & goto parse_loop
)

REM Otherwise assume it's a device serial
set DEVICE_SERIAL=!CURRENT_ARG!
set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=!CURRENT_ARG!
shift & goto parse_loop

:parse_done
echo !BLUE!------------------------------------------------------------------------------!RESET!
echo Running: %TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
echo Using flavor: %FLAVOR% (%BUILD_VARIANT%)
echo Push mode: %PUSH_MODE%   Keep app: %KEEP_APP%
echo Device serial: %DEVICE_SERIAL%
echo !BLUE!------------------------------------------------------------------------------!RESET!

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

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo !BLUE![%TIME%] Building APKs...!RESET!
set SECTION_START=%TIME%
call gradlew.bat :app:assemble%BUILD_VARIANT% :app:assemble%BUILD_VARIANT%AndroidTest
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)
call :elapsed "!SECTION_START!" "Building APKs"

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo !BLUE![%TIME%] Verifying APK files were built...!RESET!
set SECTION_START=%TIME%
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
call :elapsed "!SECTION_START!" "Verifying APK files"

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo !BLUE![%TIME%] Installing APKs...!RESET!
set SECTION_START=%TIME%

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
        adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
        exit /b 1
    )
    adb -s "!DEVICE_SERIAL!" install -r -g --no-streaming "%APK_TEST_PATH%"
    if errorlevel 1 (
        echo Push install failed - androidTest APK
        adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
        exit /b 1
    )
    echo Re-enabling Play Protect...
    adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
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
    adb -s "!DEVICE_SERIAL!" install -r -g "%APK_APP_PATH%"
    if errorlevel 1 (
        echo Install failed - app APK
        echo Path was: %APK_APP_PATH%
        adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
        exit /b 1
    )
    echo App APK installed.
    echo Installing androidTest APK...
    adb -s "!DEVICE_SERIAL!" install -r -g "%APK_TEST_PATH%"
    if errorlevel 1 (
        echo Install failed - androidTest APK
        echo Path was: %APK_TEST_PATH%
        adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
        exit /b 1
    )
    echo androidTest APK installed.
    echo Re-enabling Play Protect...
    adb -s "!DEVICE_SERIAL!" shell settings put global package_verifier_enable 1
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
call :elapsed "!SECTION_START!" "Installing APKs"

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo !BLUE![%TIME%] Running test...!RESET!
set SECTION_START=%TIME%

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
call :elapsed "!SECTION_START!" "Running test"

echo !BLUE!------------------------------------------------------------------------------!RESET!
echo [%TIME%] Done. Started at %START_TIME%
call :elapsed "!START_TIME!" "Total"
echo !BLUE!------------------------------------------------------------------------------!RESET!
exit /b %ERRORLEVEL%

REM ────────────────────────────────────────────────
REM Subroutine: compute elapsed time
REM Call: call :elapsed "HH:MM:SS.CC" "Label"
REM ────────────────────────────────────────────────
:elapsed
set _START=%~1
set _LABEL=%~2
for /f "tokens=1-4 delims=:.," %%a in ("!_START!") do (
    set /a _SH=%%a, _SM=%%b, _SS=%%c
)
for /f "tokens=1-4 delims=:.," %%a in ("%TIME%") do (
    set /a _EH=%%a, _EM=%%b, _ES=%%c
)
set /a _TOTAL_S=(_EH-_SH)*3600+(_EM-_SM)*60+(_ES-_SS)
if !_TOTAL_S! LSS 0 set /a _TOTAL_S+=86400
set /a _MIN=_TOTAL_S/60, _SEC=_TOTAL_S%%60
if !_MIN! GTR 0 (
    echo !_LABEL! took !ORANGE!!_MIN!min !_SEC!sec!RESET!
) else (
    echo !_LABEL! took !ORANGE!!_SEC!sec!RESET!
)
goto :eof
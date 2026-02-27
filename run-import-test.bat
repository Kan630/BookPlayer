@echo off
REM Run ImportBookTest (or other instrumented tests) outside IDE.
REM
REM Usage examples:
REM run-import-test.bat - run ImportBookTest (default MODE=test)
REM run-import-test.bat build - run ImportBookTest with MODE=build
REM run-import-test.bat <serial> - run ImportBookTest on device (e.g. FPMPH18C17900002)
REM run-import-test.bat <serial> keep - same, keep app, no uninstall
REM run-import-test.bat ImportBookTest <serial> - run that test class on device
REM run-import-test.bat ImportBookTest <serial> keep
REM
REM Flavors supported: Full (default), Legacy, Pure – edit FLAVOR below if needed
REM
REM Ensure a device/emulator is connected: adb devices
REM
REM cd StudioProjects\BookPlayer
REM run-import-test.bat LoadManyBookTest 52003931eec16435 keep
setlocal EnableExtensions DisableDelayedExpansion
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
echo Total arguments: %*
echo ------------------------------------------------------------------------------
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
    REM Arg1: known test class name, or device serial
    if "%~1"=="ImportBookTest" (
        set TEST_CLASS=com.driot.bookplayer.test.ImportBookTest
        if not "%~2"=="" (
            set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
            set DEVICE_SERIAL=%~2
        )
    ) else if "%~1"=="JustOpenAndWait" (
        set TEST_CLASS=com.driot.bookplayer.test.JustOpenAndWait
        if not "%~2"=="" (
            set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
            set DEVICE_SERIAL=%~2
        )
    ) else if "%~1"=="LoadManyBookTest" (
        set TEST_CLASS=com.driot.bookplayer.test.LoadManyBookTest
        if not "%~2"=="" (
            set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~2
            set DEVICE_SERIAL=%~2
        )
    ) else (
        REM Unknown first arg = assume device serial
        set DEVICE_ARG=-Pandroid.testInstrumentationRunnerArguments.deviceSerial=%~1
        set DEVICE_SERIAL=%~1
    )
)
if "%~2"=="keep" set KEEP_APP=1
if "%~3"=="keep" set KEEP_APP=1

echo ------------------------------------------------------------------------------
echo Running: %TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
echo Using flavor: %FLAVOR% (%BUILD_VARIANT%)
echo ------------------------------------------------------------------------------
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
    echo Checking directory contents:
    if exist "%CD%\app\build\outputs\apk\%FLAVOR_LOWER%\debug\" (
        dir "%CD%\app\build\outputs\apk\%FLAVOR_LOWER%\debug\"
    )
    exit /b 1
)
if exist "%APK_TEST_PATH%" (
    echo Test APK found: %APK_TEST_PATH%
    dir "%APK_TEST_PATH%" | findstr /C:"app-%FLAVOR_LOWER%-debug-androidTest.apk"
    echo WARNING: Check size above - if over 1GB, this is abnormally large!
) else (
    echo ERROR: Test APK not found: %APK_TEST_PATH%
    echo Checking directory contents:
    if exist "%CD%\app\build\outputs\apk\androidTest\%FLAVOR_LOWER%\debug\" (
        dir "%CD%\app\build\outputs\apk\androidTest\%FLAVOR_LOWER%\debug\"
    )
    exit /b 1
)
echo ------------------------------------------------------------------------------
echo [%TIME%] Installing APKs...
if not "%DEVICE_SERIAL%"=="" (
    REM Check device storage and clean up temp files
    echo Checking device storage on %DEVICE_SERIAL%...
    adb -s "%DEVICE_SERIAL%" shell df /data 2>nul | findstr /C:"/data"
    echo Cleaning up ADB temp files...
    adb -s "%DEVICE_SERIAL%" shell rm -rf /data/local/tmp/*.apk 2>nul
    adb -s "%DEVICE_SERIAL%" shell rm -rf /sdcard/Download/*.apk 2>nul
   
    REM Use the paths we already verified exist
    echo Installing app APK on %DEVICE_SERIAL% ...
    echo APK path: %APK_APP_PATH%
    if not exist "%APK_APP_PATH%" (
        echo ERROR: APK file disappeared: %APK_APP_PATH%
        exit /b 1
    )
    adb -s "%DEVICE_SERIAL%" install -r -g --no-streaming "%APK_APP_PATH%"
    if errorlevel 1 (
        echo Install failed - app APK
        echo Attempted path: %APK_APP_PATH%
        exit /b 1
    )
    echo App APK install finished at %TIME%
    echo Installing androidTest APK on %DEVICE_SERIAL% ...
    echo APK path: %APK_TEST_PATH%
    if not exist "%APK_TEST_PATH%" (
        echo ERROR: APK file disappeared: %APK_TEST_PATH%
        exit /b 1
    )
    adb -s "%DEVICE_SERIAL%" install -r -g --no-streaming "%APK_TEST_PATH%"
    if errorlevel 1 (
        echo Install failed - androidTest APK
        echo Attempted path: %APK_TEST_PATH%
        exit /b 1
    )
    echo androidTest APK install finished at %TIME%
    echo Verifying test package install...
    adb -s "%DEVICE_SERIAL%" shell pm path com.driot.bookplayer.debug.test
) else (
    call gradlew.bat :app:install%BUILD_VARIANT% :app:install%BUILD_VARIANT%AndroidTest
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
    call gradlew.bat :app:connected%BUILD_VARIANT%AndroidTest -x :app:install%BUILD_VARIANT% -x :app:install%BUILD_VARIANT%AndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
) else (
    call gradlew.bat :app:connected%BUILD_VARIANT%AndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS% %EXTRA_ARGS% %DEVICE_ARG%
)
echo ------------------------------------------------------------------------------
echo [%TIME%] Done. Started at %START_TIME%
echo ------------------------------------------------------------------------------
exit /b %ERRORLEVEL%
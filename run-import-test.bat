@echo off
REM Run ImportBookTest (or other instrumented tests) outside IDE.
REM
REM Usage:
REM   run-import-test.bat              - run ImportBookTest (default MODE=test)
REM   run-import-test.bat build        - run ImportBookTest with MODE=build
REM   run-import-test.bat test         - run ImportBookTest with MODE=test
REM   run-import-test.bat JustOpenAndWait   - run that specific test class
REM
REM Ensure a device/emulator is connected: adb devices

setlocal

set TEST_CLASS=com.driot.bookplayer.test.ImportBookTest
set EXTRA_ARGS=

if "%~1"=="build" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=build
) else if "%~1"=="test" (
    set EXTRA_ARGS=-Pandroid.testInstrumentationRunnerArguments.MODE=test
) else if not "%~1"=="" (
    set TEST_CLASS=com.driot.bookplayer.test.%~1
)

echo ------------------------------------------------------------------------------
echo Running: %TEST_CLASS% %EXTRA_ARGS%
echo ------------------------------------------------------------------------------
echo Cleaning test state...
adb uninstall com.driot.bookplayer.debug 2>nul
adb uninstall com.driot.bookplayer.debug.test 2>nul

echo ------------------------------------------------------------------------------
echo Building APKs...
call gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo Installing APKs...
call gradlew.bat :app:installDebug :app:installDebugAndroidTest
if errorlevel 1 (
    echo Install failed!
    exit /b 1
)

echo ------------------------------------------------------------------------------
echo Running test...
call gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%TEST_CLASS% %EXTRA_ARGS%

echo ------------------------------------------------------------------------------
echo Done. Check output above for results.
exit /b %ERRORLEVEL%

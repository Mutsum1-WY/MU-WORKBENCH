@echo off
rem ===========================================================================
rem  MU WORKBENCH - build a debug APK (ASCII only on purpose, cmd.exe safe)
rem  Everything (JDK / Android SDK / Gradle cache) lives inside this folder.
rem ===========================================================================
setlocal
cd /d "%~dp0"

set "JAVA_HOME=%~dp0.toolchain\jdk"
set "ANDROID_HOME=%~dp0.toolchain\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "GRADLE_USER_HOME=%~dp0.gradle-home"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [X] JDK not found at "%JAVA_HOME%"
  echo     Run _setup-toolchain.ps1 first.
  if not defined MU_NO_PAUSE pause
  exit /b 1
)
if not exist "%ANDROID_HOME%\platforms\android-35" (
  echo [X] Android SDK platform 35 not found at "%ANDROID_HOME%"
  echo     Run _setup-toolchain.ps1 first.
  if not defined MU_NO_PAUSE pause
  exit /b 1
)

echo [1/3] sync www ...
call node sync-www.cjs || goto :fail
call npx cap sync android || goto :fail

echo [2/3] gradle assembleDebug ...
pushd android
call gradlew.bat assembleDebug --no-daemon || (popd & goto :fail)
popd

echo [3/3] copy APK ...
set "APK=android\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" goto :fail
copy /y "%APK%" "MU-WORKBENCH-debug.apk" >nul
echo.
echo [OK] APK ready: %~dp0MU-WORKBENCH-debug.apk
echo      Install with: adb install -r "%~dp0MU-WORKBENCH-debug.apk"
if not defined MU_NO_PAUSE pause
exit /b 0

:fail
echo.
echo [X] build failed, see the messages above.
if not defined MU_NO_PAUSE pause
exit /b 1

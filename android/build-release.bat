@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Studibox - sborka Release APK
echo ============================================
echo.

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [1/4] gradle-wrapper.jar otsutstvuet, sozdayu wrapper...
    where gradle >NUL 2>&1
    if errorlevel 1 (
        echo.
        echo Gradle ne nayden v PATH.
        echo Otkroyte proekt F:\AIAPP2\android v Android Studio - on sozdast wrapper avtomaticheski,
        echo libo ustanovite Gradle 8.9 i zapustite etot fayl snova.
        exit /b 1
    )
    call gradle wrapper --gradle-version 8.9
    if errorlevel 1 exit /b 1
)

echo [2/4] gradlew clean
call gradlew.bat clean
if errorlevel 1 exit /b 1

echo [3/4] gradlew test (proverka dat i parsera)
call gradlew.bat testReleaseUnitTest
if errorlevel 1 exit /b 1

echo [4/4] gradlew assembleRelease
call gradlew.bat assembleRelease
if errorlevel 1 exit /b 1

echo.
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo GOTOVO: %CD%\app\build\outputs\apk\release\app-release.apk
    for %%A in ("app\build\outputs\apk\release\app-release.apk") do echo Razmer: %%~zA bayt
) else (
    echo OSHIBKA: APK ne nayden.
    exit /b 1
)
endlocal

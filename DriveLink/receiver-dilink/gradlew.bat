@rem Gradle wrapper bootstrap script for Windows
@echo off
setlocal
set DIR=%~dp0
set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  where gradle >nul 2>nul
  if %errorlevel% neq 0 (
    echo Error: %WRAPPER_JAR% not found and gradle is not in PATH.
    exit /b 1
  )
  pushd "%DIR%"
  call gradle wrapper --gradle-version 8.10.2
  popd
)

java -Xmx2048m -jar "%WRAPPER_JAR%" %*

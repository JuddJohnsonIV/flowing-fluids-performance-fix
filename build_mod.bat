@echo off
echo Setting up Gradle wrapper...

REM Download Gradle 7.6.1 if not already downloaded
if not exist gradle-7.6.1-bin.zip (
    echo Downloading Gradle 7.6.1...
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-7.6.1-bin.zip' -OutFile 'gradle-7.6.1-bin.zip'"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download Gradle 7.6.1
        exit /b 1
    )
)

REM Extract Gradle
if not exist gradle-7.6.1 (
    echo Extracting Gradle...
    powershell -Command "Expand-Archive -Path 'gradle-7.6.1-bin.zip' -DestinationPath '.'"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to extract Gradle
        exit /b 1
    )
)

REM Create Gradle wrapper
echo Creating Gradle wrapper...
call gradle-7.6.1\bin\gradle wrapper --gradle-version=7.6.1
if %ERRORLEVEL% NEQ 0 (
    echo Failed to create Gradle wrapper
    exit /b 1
)

REM Build the project
echo Building the mod...
call gradlew.bat build --stacktrace
if %ERRORLEVEL% NEQ 0 (
    echo Build failed
    exit /b 1
)

echo Build completed successfully!
pause

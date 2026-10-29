@echo off
rem Starts EduTrack without a console window. Builds the application jar first if it does not exist yet.
rem PostgreSQL must be running and %USERPROFILE%\.edutrack\application.properties must hold the database password.
setlocal
set "ROOT=%~dp0.."
set "JAR=%ROOT%\edutrack-desktop\target\edutrack-desktop-0.1.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    echo Building EduTrack for the first time. This takes a minute...
    call mvn -q -f "%ROOT%\pom.xml" install -DskipTests
    if errorlevel 1 (
        echo The build failed. Run "mvn install" in "%ROOT%" to see the details.
        pause
        exit /b 1
    )
)

start "" javaw -jar "%JAR%"

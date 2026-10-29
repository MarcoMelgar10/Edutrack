@echo off
rem Double-click wrapper for first-run.ps1 (database setup for a new EduTrack installation).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0first-run.ps1"
pause

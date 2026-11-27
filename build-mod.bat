@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat build stageDistribution --console=plain
exit /b %ERRORLEVEL%

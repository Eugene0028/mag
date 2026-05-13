@echo off
chcp 65001 >nul
cd /d "%~dp0"
javac -encoding UTF-8 TardosFingerprintLab.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 TardosFingerprintLab

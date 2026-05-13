@echo off
REM UTF-8 in консоли Windows, чтобы русские подсказки отображались нормально
chcp 65001 >nul
cd /d "%~dp0"
javac -encoding UTF-8 WatermarkEngine.java WatermarkLab.java WatermarkResearch.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 WatermarkLab

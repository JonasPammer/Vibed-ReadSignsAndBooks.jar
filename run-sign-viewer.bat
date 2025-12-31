@echo off
REM Launch Minecraft Sign Viewer
REM Usage: run-sign-viewer.bat [path-to-all_signs.csv]

java -cp build\classes\groovy\main;build\resources\main;%USERPROFILE%\.gradle\caches\modules-2\files-2.1\* viewers.SignViewer %*

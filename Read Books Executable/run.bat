@echo off
REM Run script for ReadSignsAndBooks.jar
REM This script runs the JAR file and displays the output

echo === Running ReadSignsAndBooks ===
echo.

REM Check if JAR exists
if not exist "ReadSignsAndBooks.jar" (
    echo ERROR: ReadSignsAndBooks.jar not found!
    echo Please run build.bat first to create the JAR file.
    echo.
    pause
    exit /b 1
)

REM Check if json library exists
if not exist "json-20180130.jar" (
    echo ERROR: json-20180130.jar not found!
    echo This library is required to run the application.
    echo.
    pause
    exit /b 1
)

REM Check if region folder exists
if not exist "region" (
    echo WARNING: region folder not found!
    echo Creating empty region folder...
    mkdir region
)

REM Check if playerdata folder exists
if not exist "playerdata" (
    echo WARNING: playerdata folder not found!
    echo Creating empty playerdata folder...
    mkdir playerdata
)

echo Running the application...
echo.
java -jar ReadSignsAndBooks.jar

echo.
echo === Execution Complete ===
echo.
echo Output files:
if exist "bookOutput.txt" (
    echo   - bookOutput.txt [CREATED]
) else (
    echo   - bookOutput.txt [NOT FOUND]
)
if exist "signOutput.txt" (
    echo   - signOutput.txt [CREATED]
) else (
    echo   - signOutput.txt [NOT FOUND]
)
if exist "playerdataOutput.txt" (
    echo   - playerdataOutput.txt [CREATED]
) else (
    echo   - playerdataOutput.txt [NOT FOUND]
)
echo.
pause


@echo off
REM Build script for Read Books Executable
REM This script compiles the Java source files and creates a JAR file

echo === Building Read Books Executable ===
echo.

REM Clean previous build
echo Cleaning previous build...
if exist bin (
    rmdir /s /q bin
)
mkdir bin

REM Compile Java source files
echo.
echo Compiling Java source files...
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin -cp json-20180130.jar -source 8 -target 8 @sources.txt
del sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!

REM Create JAR file
echo.
echo Creating JAR file...
jar cfm ReadSignsAndBooks.jar MANIFEST.MF -C bin .

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo JAR creation failed!
    pause
    exit /b 1
)

echo.
echo Build complete! JAR file created: ReadSignsAndBooks.jar
echo.
echo To run the JAR file, use:
echo   java -jar ReadSignsAndBooks.jar
echo.
pause


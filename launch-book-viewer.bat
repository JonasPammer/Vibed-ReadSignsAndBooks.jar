@echo off
REM Launch the Minecraft Book Viewer
REM Usage: launch-book-viewer.bat [path-to-books.json]

setlocal

REM Find the JAR file
if exist "ReadSignsAndBooks.jar" (
    set JAR_FILE=ReadSignsAndBooks.jar
) else if exist "build\libs\ReadSignsAndBooks.jar" (
    set JAR_FILE=build\libs\ReadSignsAndBooks.jar
) else (
    echo Error: ReadSignsAndBooks.jar not found
    echo Please run: gradlew fatJar
    exit /b 1
)

REM Launch with optional JSON file argument
if "%~1"=="" (
    java -cp "%JAR_FILE%" viewers.BookViewer
) else (
    java -cp "%JAR_FILE%" viewers.BookViewer "%~1"
)

endlocal

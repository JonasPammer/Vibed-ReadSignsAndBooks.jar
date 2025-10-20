# Build script for Read Books Executable
# This script compiles the Java source files and creates a JAR file

Write-Host "=== Building Read Books Executable ===" -ForegroundColor Cyan

# Clean previous build
Write-Host "`nCleaning previous build..." -ForegroundColor Yellow
if (Test-Path "bin") {
    Remove-Item -Recurse -Force "bin\*"
} else {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

# Compile Java source files
Write-Host "`nCompiling Java source files..." -ForegroundColor Yellow
$sourceFiles = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -d bin -cp "json-20180130.jar" -source 8 -target 8 $sourceFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompilation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful!" -ForegroundColor Green

# Create JAR file
Write-Host "`nCreating JAR file..." -ForegroundColor Yellow
jar cfm ReadSignsAndBooks.jar MANIFEST.MF -C bin .

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nJAR creation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`nBuild complete! JAR file created: ReadSignsAndBooks.jar" -ForegroundColor Green
Write-Host "`nTo run the JAR file, use:" -ForegroundColor Cyan
Write-Host "  java -jar ReadSignsAndBooks.jar" -ForegroundColor White


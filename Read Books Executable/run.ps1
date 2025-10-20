# Run script for ReadSignsAndBooks.jar
# This script runs the JAR file and displays the output

Write-Host "=== Running ReadSignsAndBooks ===" -ForegroundColor Cyan
Write-Host ""

# Check if JAR exists
if (-not (Test-Path "ReadSignsAndBooks.jar")) {
    Write-Host "ERROR: ReadSignsAndBooks.jar not found!" -ForegroundColor Red
    Write-Host "Please run build.ps1 first to create the JAR file." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if json library exists
if (-not (Test-Path "json-20180130.jar")) {
    Write-Host "ERROR: json-20180130.jar not found!" -ForegroundColor Red
    Write-Host "This library is required to run the application." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if region folder exists
if (-not (Test-Path "region")) {
    Write-Host "WARNING: region folder not found!" -ForegroundColor Yellow
    Write-Host "Creating empty region folder..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path "region" | Out-Null
}

# Check if playerdata folder exists
if (-not (Test-Path "playerdata")) {
    Write-Host "WARNING: playerdata folder not found!" -ForegroundColor Yellow
    Write-Host "Creating empty playerdata folder..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path "playerdata" | Out-Null
}

Write-Host "Running the application..." -ForegroundColor Green
Write-Host ""

# Run the JAR
java -jar ReadSignsAndBooks.jar

Write-Host ""
Write-Host "=== Execution Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Output files:" -ForegroundColor Yellow

if (Test-Path "bookOutput.txt") {
    Write-Host "  - bookOutput.txt [CREATED]" -ForegroundColor Green
} else {
    Write-Host "  - bookOutput.txt [NOT FOUND]" -ForegroundColor Red
}

if (Test-Path "signOutput.txt") {
    Write-Host "  - signOutput.txt [CREATED]" -ForegroundColor Green
} else {
    Write-Host "  - signOutput.txt [NOT FOUND]" -ForegroundColor Red
}

if (Test-Path "playerdataOutput.txt") {
    Write-Host "  - playerdataOutput.txt [CREATED]" -ForegroundColor Green
} else {
    Write-Host "  - playerdataOutput.txt [NOT FOUND]" -ForegroundColor Red
}

Write-Host ""
Read-Host "Press Enter to exit"


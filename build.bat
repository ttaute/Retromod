@echo off
REM ============================================================================
REM RetroMod Build Script (Windows)
REM Copyright (c) 2026 RevivalSMP. MIT License.
REM
REM Builds all RetroMod outputs:
REM   - CLI tool (standalone, all platforms)
REM   - Fabric mod (for Fabric Loader)
REM   - NeoForge mod (for NeoForge Loader)
REM ============================================================================

set VERSION=1.0.0-beta.1

echo ============================================
echo   RetroMod Build Script v%VERSION%
echo   MIT License - RevivalSMP
echo ============================================
echo.

REM Check for Maven
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven not found!
    echo Please install Maven: https://maven.apache.org/install.html
    exit /b 1
)

echo Building RetroMod...
echo.

echo [1/5] Cleaning previous build...
call mvn clean -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Clean failed!
    exit /b 1
)

echo [2/5] Compiling source code...
call mvn compile -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Compile failed!
    exit /b 1
)

echo [3/5] Running tests...
call mvn test -q
if %ERRORLEVEL% neq 0 (
    echo Warning: Some tests failed, continuing...
)

echo [4/5] Packaging JARs...
call mvn package -q -DskipTests
if %ERRORLEVEL% neq 0 (
    echo ERROR: Packaging failed!
    exit /b 1
)

echo [5/5] Creating distribution packages...

REM Create dist folder
if not exist dist mkdir dist

REM The main shaded JAR becomes the CLI tool
if exist "target\retromod-%VERSION%-all.jar" (
    copy "target\retromod-%VERSION%-all.jar" "dist\retromod-%VERSION%-cli.jar" >nul
    echo   + CLI tool: dist\retromod-%VERSION%-cli.jar
)

REM Create Fabric mod JAR
echo   Creating Fabric mod...
if exist "target\fabric-build" rmdir /s /q "target\fabric-build"
mkdir "target\fabric-build"
cd "target\fabric-build"

jar xf "..\retromod-%VERSION%.jar"

REM Remove NeoForge-specific files
if exist "META-INF\neoforge.mods.toml" del "META-INF\neoforge.mods.toml"
if exist "com\retromod\core\RetroModNeoForge.class" del "com\retromod\core\RetroModNeoForge.class"

REM Repackage as Fabric mod
jar cfm "..\..\dist\retromod-%VERSION%-fabric.jar" META-INF\MANIFEST.MF .
cd ..\..
echo   + Fabric mod: dist\retromod-%VERSION%-fabric.jar

REM Create NeoForge mod JAR
echo   Creating NeoForge mod...
if exist "target\neoforge-build" rmdir /s /q "target\neoforge-build"
mkdir "target\neoforge-build"
cd "target\neoforge-build"

jar xf "..\retromod-%VERSION%.jar"

REM Remove Fabric-specific files
if exist "fabric.mod.json" del "fabric.mod.json"

REM Update manifest for NeoForge
(
echo Manifest-Version: 1.0
echo Implementation-Title: RetroMod
echo Implementation-Version: 1.0.0
echo Automatic-Module-Name: retromod
) > META-INF\MANIFEST.MF

REM Repackage as NeoForge mod
jar cfm "..\..\dist\retromod-%VERSION%-neoforge.jar" META-INF\MANIFEST.MF .
cd ..\..
echo   + NeoForge mod: dist\retromod-%VERSION%-neoforge.jar

REM Copy icon to dist
if exist "assets\icon_512.png" copy "assets\icon_512.png" dist\ >nul

echo.
echo ============================================
echo   Build Complete!
echo ============================================
echo.
echo Output files in dist\:
dir /b dist\
echo.
echo Upload to Modrinth:
echo   - Fabric users:   retromod-%VERSION%-fabric.jar
echo   - NeoForge users: retromod-%VERSION%-neoforge.jar
echo.
echo For CLI users (power users):
echo   - All platforms:  retromod-%VERSION%-cli.jar
echo.
echo Usage:
echo   CLI:      java -jar retromod-%VERSION%-cli.jar ^<command^>
echo   Fabric:   Drop in mods\ folder with Fabric Loader
echo   NeoForge: Drop in mods\ folder with NeoForge

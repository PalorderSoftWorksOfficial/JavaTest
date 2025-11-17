@echo off
setlocal enabledelayedexpansion

REM Set the JAR filename
set NAME_JAR=JavaTest.jar

REM Set output folders
set BUILD_DIR=build
set LIBS_DIR=%BUILD_DIR%\libs

REM Make sure output folders exist
if not exist %LIBS_DIR% mkdir %LIBS_DIR%

REM Compile all Java files recursively, including any jars in libs for compilation
set CP=
for %%J in (%LIBS_DIR%\*.jar) do (
    set CP=!CP!;%%J
)

for /R %%f in (*.java) do (
    if defined CP (
        javac -d %BUILD_DIR% -cp ".!CP!" %%f
    ) else (
        javac -d %BUILD_DIR% %%f
    )
)

REM Initialize JAR command with entry point
set JAR_CMD=jar cfe %LIBS_DIR%\%NAME_JAR% main.Main

REM Include all directories in build except 'libs'
for /D %%D in (%BUILD_DIR%\*) do (
    if /I not "%%~nxD"=="libs" (
        set JAR_CMD=!JAR_CMD! -C %BUILD_DIR% %%~nxD
    )
)

REM Include any files directly inside BUILD_DIR (excluding 'libs')
for %%F in (%BUILD_DIR%\*) do (
    if not "%%~nxF"=="libs" if not "%%~aF"=="d" (
        set JAR_CMD=!JAR_CMD! -C %BUILD_DIR% %%~nxF
    )
)

REM Include all JARs inside libs/ into the final JAR (unpacked)
for %%J in (%LIBS_DIR%\*.jar) do (
    echo Adding %%~nxJ to JAR...
    pushd %LIBS_DIR%
    jar xf %%~nxJ
    popd
)

REM Execute JAR command
%JAR_CMD%

echo Compilation finished! JAR is at %LIBS_DIR%\%NAME_JAR%

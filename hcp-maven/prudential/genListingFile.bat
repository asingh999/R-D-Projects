@echo off

if "%~1" == "" (
  echo.
  echo This tool generates listing file used for CMX/Alliance Calls
  echo.
  echo Usage %~n0 ^<Data-Set-Name^>
  echo    where,
  echo      ^<Data-Set-Name^> is InboundCalls or OutboundCalls
  echo.

  EXIT /b 1
)

call config\envsetup_CMX %1

if NOT EXIST %DATA_FOLDER% (
   echo ERROR: Data Folder for <Data-Set-Name> is not found.
   echo     %DATA_FOLDER%
   echo.

   exit /b 1
)

set CLASSPATH=%TOOL_LIBS%\Prudential.jar

set DEFINES=

java %DEFINES% com.prudential.tools.MDConverter %DATA_FOLDER%\%DATA_SET%.XML

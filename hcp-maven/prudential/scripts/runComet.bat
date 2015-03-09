@echo off

set /a ARGS_COUNT=0
for %%A in (%*) DO SET /A ARGS_COUNT+=1

if %ARGS_COUNT% NEQ 2 (
  echo ERROR: Invalid number of input parameters.
  echo.
  echo Usage %~n0 ^<Call-Source-Name^> ^<Data-Set-Name^>
  echo    where,
  echo      ^<Call-Source-Name^> is 'CMX' or 'Sykes'
  echo      ^<Data-Set-Name^> is 
  echo          for CMX: 'InboundCalls' or 'OutboundCalls'
  echo          for Sykes: 'NICECalls'
  echo.

  exit /b 1
)

REM Make sure the first parameter is correct.
if "%1" NEQ "CMX" if "%1" NEQ "Sykes" (
   echo ERROR: Invalid ^<Call-Source-Name^> specified on command line.
   echo     %1
   echo.

   exit /b 1
)

call config/envsetup_%1 %2

if NOT EXIST "%DATA_FOLDER%" (
   echo ERROR: Data Folder for ^<Data-Set-Name^> is not found.
   echo     %DATA_FOLDER%
   echo.

   exit /b 1
)

if "%LISTING_FILE%" NEQ "" if NOT EXIST "%LISTING_FILE%" (
   echo ERROR: Listing file is not found.
   echo     %LISTING_FILE%"
   echo.
   exit /b 1
)

REM This constant defines the class path entries for the custom modules that will
REM   do the metadata processing. This line must be modified in order to pick up
REM   the java entry points to these modules.
set CUSTOM_MODULES=%TOOL_LIBS%\Prudential.jar

set CLASSPATH=%COMET_LIBS%\COMET.jar;%COMET_LIBS%\HCPAPIHelpers.jar
set CLASSPATH=%CLASSPATH%;%COMET_LIBS%\httpcore-4.2.4.jar;%COMET_LIBS%\httpclient-4.2.5.jar;%COMET_LIBS%\commons-codec-1.6.jar;%COMET_LIBS%\commons-logging-1.1.1.jar
set CLASSPATH=%CLASSPATH%;%COMET_LIBS%\log4j-api-2.0.2.jar;%COMET_LIBS%\log4j-core-2.0.2.jar
set CLASSPATH=%CLASSPATH%;%CUSTOM_MODULES%

set DEFINES=-Dlog4j.configurationFile=file://%TOOL_CONFIG%\log4j2.xml -Dcom.hds.hcp.tools.comet.properties.file=%TOOL_CONFIG%\comet_%CALL_SOURCE%.properties -Dcom.prudential.properties.file=%TOOL_CONFIG%\prudential_%CALL_SOURCE%.properties -Dcom.hds.hcp.tools.comet.scanner.properties.file=%TOOL_CONFIG%\scanner_%CALL_SOURCE%.properties

REM echo CLASSPATH=%CLASSPATH%
REM echo DEFINES=%DEFINES%

java %DEFINES% com.hds.hcp.tools.comet.CometMain 

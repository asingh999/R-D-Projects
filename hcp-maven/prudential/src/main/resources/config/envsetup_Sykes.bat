@echo off

REM
REM Need at least a data set input parameter.
REM
if "%~1" == "" (
   ECHO %~n0 bad usage.  Must provide input parameter.
   EXIT /B 1
)

set DATA_SET=%1

set TOOL_HOME=%cd%
set TOOL_LIBS=%TOOL_HOME%\lib
set COMET_LIBS=%TOOL_LIBS%
set TOOL_CONFIG=%TOOL_HOME%\config
set TOOL_LOGS=%TOOL_HOME%\logs
set COMET_LOGS=%TOOL_LOGS%

set CALL_SOURCE=Sykes

set BASE_DATA_FOLDER=..\TestData\%CALL_SOURCE%Data
REM set BASE_DATA_FOLDER=F:\%CALL_SOURCE%Data
set DATA_FOLDER=%BASE_DATA_FOLDER%\%DATA_SET%

set HCP_DNS_NAME=njhcp.prudential.com
set HCP_TENANT_NAME=ili-call
set HCP_NAMESPACE_NAME=XXX

set HCP_USER=j350843
set HCP_ENC_PWD=79009DA9A44DB82A6F76CD205DB4F15F

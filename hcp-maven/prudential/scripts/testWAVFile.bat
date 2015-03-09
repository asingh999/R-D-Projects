@echo off

set WORKSPACE_LOC=%cd%
set PROJECT_LOC=%WORKSPACE_LOC%

set MY_JAVA_LIBS=%WORKSPACE_LOC%\lib

REM  This constant defines the class path entries for the custom modules that will
REM  do the metadata processing. This line must be modified in order to pick up
REM  the java entry points to these modules.
set CUSTOM_MODULES=

set CLASSPATH=%MY_JAVA_LIBS%\Prudential.jar

set DEFINES=

java %DEFINES% com.prudential.tools.GetWAVDuration %*

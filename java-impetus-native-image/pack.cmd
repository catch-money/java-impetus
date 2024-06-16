@REM ****************************************************************
@echo off
if not defined VC_HOME (
 echo "VC_HOME is not defined"
 exit /B 1
)

if not defined GRAALVM_HOME (
 echo "GRAALVM_HOME is not defined"
 exit /B 1
)

set "VC=%VC_HOME%\VC\Auxiliary\Build\vcvars64.bat"
echo VC: %VC%
set graalvm_jdk_home=%GRAALVM_HOME%

set "JAVA_HOME=%graalvm_jdk_home%"
echo JAVA_HOME: %JAVA_HOME%
call "%VC%"
@REM plugin package
mvn clean native:compile -DskipTests -Pnative

@echo off

REM Script to run the stock ticker example.
REM

call ..\..\setenv.bat

set MEMORY_OPTIONS=-Xms256m

"%JAVA_HOME%\bin\java" %MEMORY_OPTIONS% -Dlog4j.configuration=log4j.xml com.espertech.esper.example.IOT.IotMain %1 %2
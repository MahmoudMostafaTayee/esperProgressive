@echo off
REM Run script for Esper IOT Example
REM Bypasses checkstyle and uses Maven to run the project with specified arguments.

set MAIN_CLASS=com.espertech.esper.example.IOT.IotMain
set ARGS=--scene 1 --features_dir C:\OURs\Thesis\Datasets\EmbedFeature --camera 0001 --output_dir ./outputs/scene1

mvn exec:java -Dexec.mainClass="%MAIN_CLASS%" -Dexec.args="%ARGS%" -Dcheckstyle.skip

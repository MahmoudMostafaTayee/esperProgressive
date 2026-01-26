@echo off
REM Run script for Esper IOT Example with ALL cameras
set MAIN_CLASS=com.espertech.esper.example.IOT.IotMain
set ARGS=--scene 1 --features_dir C:\OURs\Thesis\Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing\Datasets\EmbedFeature --camera all --output_dir ./outputs/scene1_all
mvn exec:java -Dexec.mainClass="%MAIN_CLASS%" -Dexec.args="%ARGS%" -Dcheckstyle.skip

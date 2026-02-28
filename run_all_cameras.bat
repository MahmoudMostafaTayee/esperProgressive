@echo off
REM Run script for Esper IOT Example with ALL cameras

echo Cleaning up port 9999...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :9999 ^| findstr LISTENING') do taskkill /f /pid %%a
set MAIN_CLASS=com.espertech.esper.example.IOT.IotMain
set ARGS=--scene 1 --features_dir C:\OURs\Thesis\Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing\Datasets\EmbedFeature --camera all --output_dir ./output/scene1

echo Starting Python Table Listener in a new window...
start "Python Table Listener" cmd /k "python listen_to_table.py"

mvn exec:java -Dexec.mainClass="%MAIN_CLASS%" -Dexec.args="%ARGS%" -Dcheckstyle.skip

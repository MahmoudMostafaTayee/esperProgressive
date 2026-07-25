@echo off
REM Run script for Esper IOT Example with ALL cameras

echo Cleaning up port 9999...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :9999 ^| findstr LISTENING') do taskkill /f /pid %%a

set MAIN_CLASS=com.espertech.esper.example.IOT.IotMain
set FEATURES_DIR=C:\OURs\Thesis\Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing\Datasets\EmbedFeature

echo Starting Python Table Listener in a new window...
start "Python Table Listener" cmd /k "python listen_to_table.py"

echo Starting Maven Execution...
mvn exec:java -Dexec.mainClass="%MAIN_CLASS%" "-Dexec.args=--scene 2 --features_dir %FEATURES_DIR% --camera '0001,0011,0002,0017,0019,0021,0025,0027,0030' --camera_groups '2,17;1,11;19,27;21,25,30'  --turbo --output_dir ./output/scene2" -Dcheckstyle.skip=true

@echo off
set PORT=9999
echo Searching for process using port %PORT%...

for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%PORT% ^| findstr LISTENING') do (
    set PID=%%a
)

if "%PID%"=="" (
    echo No process found listening on port %PORT%.
    echo Searching for any Python listener processes...
    taskkill /F /FI "COMMANDLINE eq python listen_to_table.py" >nul 2>&1
) else (
    echo Found process %PID% using port %PORT%. 
    echo Terminating...
    taskkill /F /PID %PID%
)

echo Done.

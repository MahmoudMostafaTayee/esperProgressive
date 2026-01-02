# Run script for Esper IOT Example
# Bypasses checkstyle and uses Maven to run the project with specified arguments.

$MainClass = "com.espertech.esper.example.IOT.IotMain"
$ExecArgs  = "--scene 1 --features_dir C:\OURs\Thesis\Datasets\EmbedFeature --camera 0001 --output_dir ./outputs/scene1"

mvn exec:java `
  "-Dexec.mainClass=$MainClass" `
  "-Dexec.args=$ExecArgs" `
  "-Dcheckstyle.skip=true"

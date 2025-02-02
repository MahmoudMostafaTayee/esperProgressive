#!/bin/sh

# Script to run the stock ticker example.
#
# change log4j.xml logger configuration file to DEBUG for more detailed output or INFO for less detailed output

# A note to cygwin users: please replace "-cp ${CLASSPATH}" with "-cp `cygpath -wp $CLASSPATH`"
#

. ../../setenv.sh

MEMORY_OPTIONS="-Xms256m"

$JAVA_HOME/bin/java $MEMORY_OPTIONS -Dlog4j.configuration=log4j.xml -cp ${CLASSPATH} com.espertech.esper.example.IOT.IotMain $1 $2

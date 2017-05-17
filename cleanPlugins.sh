#!/bin/bash

JAVA_HOME="/usr/lib/jvm/jdk1.7.0_80"
DIR_PROJ="../../CodeSamples/Socket/src/"
LOG="log"
MSE="default.mse"

ROOTDIR=`dirname $0`
DROPPED="$ROOTDIR/droppedPlugins.txt"

if [ -s "$DROPPED" ]
then
    echo "removing registered plugins from droppedPlugins.txt"
    for p in `cat $DROPPED`
    do
	test -d "$p" && rm -rf "$p"
	test -s "$p" && rm -f "$p"

	grep -v $p configuration/org.eclipse.equinox.simpleconfigurator/bundles.info > newbundles
	mv newbundles configuration/org.eclipse.equinox.simpleconfigurator/bundles.info
    done
else
    echo "no file $DROPPED with registered plugins to remove"
fi

exit

echo "testing all remaining plugins"
test -d backup || mkdir backup
rm -f backup/*
for p in `ls plugins/*.jar`
do
    b=`basename "$p"`
    echo "  $b"
    mv "plugins/$b" "backup/$b"
    rm -f default.mse "$LOG"
    ERROR="false"

    # try running the parser without the plugin
    ${JAVA_HOME}/bin/java -cp plugins/org.eclipse.equinox.launcher_1.3.100.v20150511-1540.jar org.eclipse.equinox.launcher.Main -application verveine.extractor.Cpp.Main -o "$MSE" "$DIR_PROJ" > "$LOG" 2>&1

    # did parsing run well?
    # no if "error" in log file
    grep -iq error "$LOG" && ERROR="true"
    # no if mse file is empty
    test -s $MSE || ERROR="true"
    # no if mse file is too small
    test "$ERROR" == "false" && test `wc -c < $MSE` -lt 1000 && ERROR="true"

    # in case of error, move the plugin back
    test "$ERROR" == "true" && mv "backup/$b" "plugins/$b"

    rm -f "backup/$b"
done


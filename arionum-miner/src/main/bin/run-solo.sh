#! /bin/bash
if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]
then
 echo Usage:
 echo   run-solo.sh node-address arionum-address arionum-privatekey
 exit 1;
fi

java -jar target/@batchJarName@.jar solo $1 $2 $3 1 stable true

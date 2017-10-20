#! /bin/bash

# Exit bash script on error:
set -e

# Create entry into database with failed boottest and export several needed envirnoment variables

# This is executed within the critical block! This avoids two boottest entries with the identical
# bootdate.

export ARCH=$(dirname "$CONFIG")
export COMPILETEST_ID="$($PGSQL -c "SELECT id FROM compiletest WHERE cirtscheduler_id="\'"$SCHEDULER_ID"\'" AND arch="\'"$ARCH"\'" AND configname="\'"$(basename $CONFIG)"\'" AND overlay="\'"$OVERLAY"\'";" RT-Test)"
export TARGETID="$($PGSQL -c "select id from target where hostname="\'"$TARGET"\'";" RT-Test)"
export BOOTDATE="$(date -u +"%Y-%m-%d %H:%M:%S")"

# Abort, if COMPILETEST_ID or TARGETID is empty!
if [ -z ${COMPILETEST_ID} ] || [ -z ${TARGETID} ]
then
	exit 1
fi

# $PGSQL -c "INSERT INTO boottest (id, cmdline, pass, target_id, compiletest_id, bootdate, owner, bootlog) VALUES("\'"$BUILD_NUMBER"\'", "\'"none"\'", "\'"0"\'", "\'"$TARGETID"\'", "\'"$COMPILETEST_ID"\'", "\'"$BOOTDATE"\'", "\'"$ENTRYOWNER"\'", "\'"not set"\'");" RT-Test

echo "COMPILETEST_ID=$COMPILETEST_ID" > compiletest_id.properties

mkdir -p result
cp compiletest_id.properties result/

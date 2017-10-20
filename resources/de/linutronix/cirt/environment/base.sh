#! /bin/bash

# Exit bash script on error:
set -e

# Sanity check; abort if there is a .properties file. Those files are generated
# by jenkins only:

if [[ -n $(find . -name *.properties) ]]
then
	exit 1
fi


# Put all relevant environment settings into a single environment.properties file
# This file is later on edited several times
cp env/global environment.properties

echo "SCHEDULER_ID=${SCHEDULER_ID}" >> environment.properties

echo "ENV_ID=${BUILD_NUMBER}" >> environment.properties

echo "CI_RT_URL=${CI_RT_URL}" >> environment.properties

# Insert PUBLICREPO if it is not set (default value is true)
grep -q '^PUBLICREPO' environment.properties || echo "PUBLICREPO=true" >> environment.properties


# If parameter COMMIT is not empty (only whitespace means empty) overwrite existing COMMIT entry
# or add it

if [ ! -z ${COMMIT} ]
then
	grep -q '^COMMIT' environment.properties && sed -i "s/^COMMIT.*/COMMIT=${COMMIT}" || echo "COMMIT=${COMMIT}" >> environment.properties
fi

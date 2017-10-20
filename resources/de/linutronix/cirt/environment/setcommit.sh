#! /bin/bash

# Exit bash script on error:
set -e

# If parameter COMMIT is set (Jenkins or test-description), COMMIT needs to be checked
# out and build, if it is not set BRANCH needs to be checked out.

if [ x"${COMMIT}" == x ]
then
    echo "GIT_CHECKOUT=${BRANCH}" >> environment.properties
else
    echo "GIT_CHECKOUT=${COMMIT}" >> environment.properties

fi

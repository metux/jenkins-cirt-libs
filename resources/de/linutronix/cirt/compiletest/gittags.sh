#! /bin/bash

# Exit bash script on error:
set -e

# create directories to store build and results
mkdir -p $RESULT
mkdir -p $BUILD

# the commit and tag description for the currently checked out git branch
# is stored to update tags and git db table in CI-RT-scheduler after
# finished compiletest
echo "TAGS_COMMIT= $(git rev-parse HEAD)" >> $RESULT/gittags.properties
echo "TAGS_NAME = $(git describe HEAD)" >> $RESULT/gittags.properties

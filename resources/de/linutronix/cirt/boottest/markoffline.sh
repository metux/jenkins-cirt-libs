#! /bin/bash

# Exit bash script on error:
set -e

# Update boottest notes: add "Target temporary not available"
# $PGSQL -c "UPDATE boottest SET notes = "\'"Target temporary not available"\'";" RT-Test
echo "[99999.999999] Target $TARGET temporary not available" > result/boot.log

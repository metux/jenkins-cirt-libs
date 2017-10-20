#! /bin/bash

set -e

ARCH=$(dirname $config)
CONFIGNAME=$(basename $config)
BOOTTESTS=" "

echo "building compile/env/$ARCH-$CONFIGNAME-$overlay.properties"

# Read env/boottest.list line by line
while read line
do
    case $line in
	# ignore comments
        "#"*)
        ;&
        "")
            continue
            ;;
        # find $config and $overlay in all other boottest files
        *)
            if grep -q -G "^CONFIG[ ]*=[ ]*${config}$" $line && grep -q -G "^OVERLAY[ ]*=[ ]*${overlay}$" $line
            then
                BOOTTESTS="$BOOTTESTS $line"
            fi
            ;;
    esac
done < env/boottest.list

# create compile/env/$ARCH-$NAME-$OVERLAY.properties file
if [ ! -z $BOOTTESTS ]
then
    echo "BOOTTESTS=$BOOTTESTS" > compile/env/$ARCH-$CONFIGNAME-$overlay.properties
else
    echo "No boottest configured: Property file creation skipped."
fi

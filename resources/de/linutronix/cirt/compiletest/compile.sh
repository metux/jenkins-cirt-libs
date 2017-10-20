#! /bin/bash

# Exit bash script on error:
set -e

# Make devicetree binaries?
if [ x = x"$MKDTBS" ]
then
	export MKDTBSCMD=""
else
	export MKDTBSCMD="make -j ${PARALLEL_MAKE_JOBS:=16} O=build LOCALVERSION=-$SCHEDULER_ID dtbs"
fi


# Build only and do not boot config? packaging of kernel is not required

if [ ! -f .env/compile/env/$ARCH-$CONFIGNAME-$overlay.properties ]
then
	BUILDONLY=1
else
	BUILDONLY=0
fi

# Create build/cmd script to build/package the kernel and dtbs
cat << _EOF_ > $BUILD/cmd
# Abort build script if there was an error executing the commands
set -e

echo "compiletest-runner #$BUILD_NUMBER $ARCH/$CONFIGNAME (stderr)" > $RESULT/compile.log
make -j ${PARALLEL_MAKE_JOBS:=16} O=$BUILD LOCALVERSION=-$SCHEDULER_ID 2> >(tee -a $RESULT/compile.log >&2)
$MKDTBSCMD

_EOF_


# If config will be booted later, debian package and devicetrees
# need to be created and stored in $RESULT
if [ $BUILDONLY -eq 0 ]
then
	cat << _EOF_ >> $BUILD/cmd
make -j ${PARALLEL_MAKE_JOBS:=16} O=$BUILD LOCALVERSION=-$SCHEDULER_ID ${BUILD_TARGET:-bindeb-pkg} 2> >(tee $RESULT/package.log >&2)

if [ -d $BUILD/arch/${ARCH}/boot/dts ]
then
	tar cJf $RESULT/dtbs-${SCHEDULER_ID}.tar.xz --exclude 'dts/.*' -C $BUILD/arch/${ARCH}/boot dts
fi
cp *deb $RESULT/
_EOF_

fi

chmod a+x $BUILD/cmd
cp $BUILD/cmd $RESULT/

# Execute Buildcmd
$BUILD/cmd

#! /bin/bash

# Prepare Kernel Config
# abort skript on error
set -e

CONFFILE=".env/compile/configs/${config}"
OVERLAYFILE=".env/compile/overlays/${overlay}"
ARCHOVERLAYFILE=".env/compile/overlays/${ARCH}/${overlay}"

tovr=$RESULT/tmpoverlay
oscript=$RESULT/overlayscript

handle_overlay() {
	if [ ! -f $OVERLAYFILE ]
	then
		echo "error: overlay $overlay not found"
		return 1
	fi

	# if arch overlay file exist use both overlays files and
	# ignore lines without CONFIG_ option(set +e is needed, because
	# an empty overlay is allowed as well)
	set +e

	if [ -f $ARCHOVERLAYFILE ]
	then
		cat "$OVERLAYFILE" "$ARCHOVERLAYFILE" | grep "CONFIG_" > $tovr
	else
		cat "$OVERLAYFILE" | grep "CONFIG_" > $tovr
	fi
	set -e

	# if overlay is empty nothing needs to be done next, return
	if [[ ! -s $tovr ]]
	then
		return 0
	fi

	# overlay is applied with scripts/config; script begin is stored in $oscript
	# use $tovr for enable, disable, module and set-val option

	grep "# CONFIG_.* is not set" $tovr | sed -e 's@^# CONFIG_\([^ ]*\).*@--disable \1 \\@' >> $oscript

	grep "=y" $tovr | sed -e 's@^CONFIG_\([^=]*\).*@--enable \1 \\@' >> $oscript

	grep "=m" $tovr | sed -e 's@^CONFIG_\([^=]*\).*@--module \1 \\@' >> $oscript

	grep "=[0-9\"]\+" $tovr | sed -e 's@^CONFIG_\([^=]*\)=\([0-9\"]\+\).*@--set-val \1 \2 \\@' >> $oscript

	chmod a+x $oscript
	./$oscript

	# use olddefconfig instead of oldconfig to avoid failures due to new konfig variables
	make ARCH=$ARCH O=$BUILD olddefconfig

	# Check if all parameters were set propertly, set err=1 if a option was not set
	# abort script after all parameters were checked
	err=0
	while read line
	do
		option=$(echo $line | sed -e 's@.*CONFIG_\([^ =]*\).*@\1@')
		ret=$(scripts/config --file ${BUILD}/.config --state $option)
		case $line in
			"# CONFIG_"*" is not set")
				if [[ $ret != "undef" ]] && [[ $ret != "n" ]]
				then
					echo $line" was not set propertly"
					err=1
				fi
				;;
			"CONFIG_"*"=y")
				;&
			"CONFIG_"*"=m")
				echo $ret
				if [[ $ret != "y" ]] && [[ $ret != "m" ]]
				then
					echo $line" was not set propertly"
					err=1
				fi
				;;
			"CONFIG_"*"="[0123456789]*)
				val=$(echo $line | sed -e 's@^CONFIG_.*=\([0-9\"]\+\).*@\1@')
				if [[ $ret != $val ]]
				then
					echo $line" was not set propertly ("$ret" instead of "$val")"
					err=1
				fi
				;;
			*)
				echo "Please check unknown option: "$line
				err=1
				;;
		esac
	done < $tovr


	if [[ $err -eq 1 ]]
	then
		return 1
	fi
	return 0
}


# create begin of overlayscript
cat << _EOF_ > $oscript
#!/bin/bash
set -e

# --file option needs to be before any other option
scripts/config --file ${BUILD}/.config \\
_EOF_

CONF=$(basename $config)

echo "Generate Kernel configuration $ARCH $CONF"

if [ -f "$CONFFILE" ]
then
	cp "$CONFFILE" "$BUILD/.config"
	make ARCH=$ARCH O=$BUILD olddefconfig
else
	case "$CONF" in
	'allnoconfig')
		;;
	'allyesconfig')
		;;
	'allmodconfig')
		;;
	'alldefconfig')
		;;
	'randconfig')
		;;
	*)
		if [ ! -f "arch/$ARCH/configs/$CONF" ]
		then
			echo "defconfig $CONF not found"
			exit 1
		fi
		;;
	esac
	make ARCH=$ARCH O=$BUILD $CONF
fi


# handle overlay
if [ x != x"$overlay" ]
then
	handle_overlay
fi

# store config in results
cp $BUILD/.config $RESULT/config

# Create and store defconfig
make -j 8 O=$BUILD LOCALVERSION=-$SCHEDULER_ID savedefconfig
cp $BUILD/defconfig $RESULT/
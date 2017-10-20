#! /bin/bash

# Exit bash script on error:
set -e

# log of serial console. Purge logfile before using it to avoid crude artefacts in logfile.
export SERIALLOG=boottest/serialboot.log
export BOOTLOG=boottest/boot.log
export BOOTLOGRAW=boot.raw

mkdir -p boottest

rm -f $SERIALLOG
rm -f $BOOTLOGRAW
rm -f target_reboot.failed

virsh -c "$HYPERVISOR" consolelog $TARGET --force --logfile $SERIALLOG &
export SERLOGPID=$!

export KERNCMDLINE="none"

# Brave New World: systemd kills the network before ssh terminates, therefore -t +1, witch is really now + a bit.
echo "Reboot Target..."
ssh $TARGET "sudo shutdown -r -t +1"

echo "Wait some time for target reboot..."
sleep 300

echo "Check if target is back online..."
export TVERSION=$(ssh -o ConnectTimeout=10 -o ConnectionAttempts=6 $TARGET uname -r | sed 's/.*-rt[0-9]\+-\([0-9]\+\).*$/\1/')

# terminate serial logging
kill $SERLOGPID

if [ "$TVERSION" != "$SCHEDULER_ID" ]
then
ssh $TARGET "sudo shutdown -r -t +1" || \
	(virsh -c "$HYPERVISOR" destroy $TARGET; sleep 1; virsh -c "$HYPERVISOR" start $TARGET)
	echo "The booted kernel version $TVERSION on target $TARGET differs from version $SCHEDULER_ID under test."
	export PASS="0"
else
	sleep 30
	echo "Target is back."

	export PASS="1"
	export KERNCMDLINE="$(ssh $TARGET cat /proc/cmdline)"

	if [ x"$KERNCMDLINE" == x ]
	then
		export PASS="0"
    fi
fi


# extract kernel output from serial log (Timestamps are enabled via command-line
# argument: printk.time=y):
# example output:
# [    3.677067] sysrq: SysRq : Emergency Sync

# remove all stuff before starting the kernel
while IFS='' read -r line || test -n "$line"
do
	# look for "[    0.000000]" lines by replace and compare.
	# if "[    0.000000]" is found (and replaced) $tst and $line
	# do NOT match.

	tst=$(echo "$line" | sed 's/\[    0.000000\]/XXX/')

	if [ "$tst" = "$line" ]
	then
		# "[    0.000000]" *not* found
		out=1
	else
		# "[    0.000000]" found
		if [ $out = "1" ]
		then
			# clear output file on first "[    0.000000]" line
			: > $BOOTLOGRAW
			out=2
		fi
	fi
	echo "$line" >> $BOOTLOGRAW
done < $SERIALLOG

# extract kernel output from
grep '\[[ ]*[0-9]\+\.[0-9]\+\]' $BOOTLOGRAW > $BOOTLOG

# Do not stop here. Set marker "target_reboot.failed" and wait some time to settle the hardware
if [ "$PASS" = "0" ]
then
	touch "target_reboot.failed"
fi

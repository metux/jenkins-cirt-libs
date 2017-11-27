#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.libvirt;

private runner(Map global, String boottest) {
	unstash(global.STASH_PRODENV);
	helper = new helper();
	String[] properties = ["environment.properties",
			       "${boottest}.properties"];
	helper.add2environment(properties);

	target = helper.getEnv("TARGET");
	if (!target?.trim()) {
		error("environment TARGET not set. Abort.");
	}

	config = helper.getEnv("CONFIG");
	overlay = helper.getEnv("OVERLAY");
	kernel = "${config}/${overlay}";
	/* Last subdirectory "boottest" for results is created by scripts */
	boottestdir = "results/${kernel}/${target}";
	resultdir = "boottest";

	hypervisor = libvirt.getURI(target);
	helper.extraEnv("HYPERVISOR", hypervisor);
	println("URI = ${hypervisor}");

	dir(boottestdir) {
		deleteDir();
		lock(target) {
			helptext = "Reboot to Kernel build (${env.BUILD_TAG})";
			libvirt.wait4onlineTimeout(target, 120);

			targetprep(global, target, kernel);

			libvirt.offline(target, helptext);

			/* Create result directory */
			dir(resultdir) {
				writeFile file:'boottest.sh', text:'';
			}
			def content = """\
#! /bin/bash

# Exit bash script on error:
set -e

# Required environment setting
export TARGET=${target}
export SCHEDULER_ID=${env.BUILD_NUMBER}
export HYPERVISOR=${hypervisor}

RESULT_DIR=${resultdir}
""" + '''

# log of serial console
export SERIALLOG=$RESULT_DIR/serialboot.log
export BOOTLOG=$RESULT_DIR/boot.log
export BOOTLOGRAW=boot.raw

virsh -c "$HYPERVISOR" consolelog $TARGET --force --logfile $SERIALLOG &
export SERLOGPID=$!

export KERNCMDLINE="none"

# Brave New World: systemd kills the network before ssh terminates, therefore -t +1, witch is really now + a bit.
echo "Reboot Target..."
ssh $TARGET "sudo shutdown -r -t +1"

echo "Wait some time for target reboot..."
sleep 300

echo "Check if target is back online..."
export TVERSION=$(ssh -o ConnectTimeout=10 -o ConnectionAttempts=6 $TARGET uname -r | sed "s/.*-rt[0-9]\\+-\\([0-9]\\+\\).*$/\\1/")

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

	tst=$(echo "$line" | sed "s/\\[    0.000000\\]/XXX/")

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
grep "\\[[ ]*[0-9]\\+\\.[0-9]\\+\\]" $BOOTLOGRAW > $BOOTLOG

# Do not stop here. Set marker "target_reboot.failed" and wait some time to settle the hardware
if [ "$PASS" = "0" ]
then
	touch "target_reboot.failed"
fi
''';
			writeFile file:"${resultdir}/boottest.sh", text:content;
			sh "${content}";

			libvirt.online(target, helptext);

			cyclictests = helper.getEnv("CYCLICTESTS").split();
			node('master') {
				/*
				 * cyclictest is executed on another
				 * node, workspace doesn't has to be
				 * changed.
				 */
				cyclictest(global, target, cyclictests);
			}
		}
	}
	archiveArtifacts(artifacts: "${boottestdir}/${resultdir}/**",
			 fingerprint: true);
}

def call(Map global, String boottest) {
	try {
		inputcheck.check(global);
		dir("boottestRunner") {
			deleteDir();
			runner(global, boottest);
		}
	} catch(Exception ex) {
		println("boottest \"${boottest}\" failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("boottest \"${boottest}\" failed.");
        }
}

def call(String... params) {
	println params
        error("Unknown signature. Abort.");
}

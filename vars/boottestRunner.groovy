#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.libvirt;

private rebootTarget(String hypervisor, String target, String seriallog) {
	def pidfile = "seriallogpid";
	def virshoutput = "virshoutput";
	off_message = "Reboot to Kernel build (${env.BUILD_TAG})";;

	/* Start serial console logging */
	/* TODO: Cleanup error handling; it is not the best solution */
	writeFile file: virshoutput, text: '';
	sh """virsh -c ${hypervisor} consolelog ${target} --force --logfile ${seriallog} 2> ${virshoutput} || echo \"virsh error!\" >> ${virshoutput}"""+'''&
echo $! >'''+""" ${pidfile}"""

	/* Wait 5 seconds to be sure that virsh workes properly and error file was written*/
	sleep(time: 5, unit: 'SECONDS');
	output = readFile(virshoutput).trim()
	if (output) {
		println("Virsh console logging Problem (target properly written and logfile writeable?): "+output);
		/* TODO: throw exception; IT Problem */
	}

	/* Set taget offline */
	libvirt.offline(target, off_message);

	/*
	 * Brave New World: systemd kills the network before ssh
	 * terminates, therefore -t +1, witch is really now + a bit;
	 */
	sh "ssh ${target} \"sudo shutdown -r -t +1\"";

	/*
	 * Sleep more than 5 Minutes - it is the Jenkins slave ping
	 * timeout. Please look at:
	 * https://wiki.jenkins.io/display/JENKINS/Ping+Thread.
	 * Furthermore, all targets should be online afterwards.
	 */
	sleep(time: 330, unit: 'SECONDS');

	println("kill seriallog");
	def pid = readFile(pidfile).trim();
	sh "kill ${pid}";
}

private writeBootlog(String seriallog, String bootlog) {
	kexec_delimiter = "--- CI-RT Booting Testkernel Kexec ---";

	/* extract kexec bootlog of serial log*/
	serial_content = readFile(seriallog);
	serial_splits = serial_content.split(kexec_delimiter);

	/* error, if kexec_delimiter do not occur, or occurs more than one time */
	def cnt = serial_splits.size() - 1;
	if (cnt != 1) {
		boot_content = serial_content;
		error message:"kexec delimiter \"${kexec_delimiter}\" occurs "+cnt+"time";
	} else {
		/* remove all lines which are no kernel output */
		boot_content = serial_splits[1].replaceAll(/(?m)^[^\[]*/, "");
	}

	writeFile file:bootlog, text:boot_content;
}


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
			seriallog = "${resultdir}/serialboot.log";
			bootlog = "${resultdir}/boot.log";

			helptext = "Reboot to Kernel build (${env.BUILD_TAG})";
			libvirt.wait4onlineTimeout(target, 120);

			targetprep(global, target, kernel);

			/* Create result directory */
			dir(resultdir) {
				writeFile file:'boottest.sh', text:'';
			}

			rebootTarget(hypervisor, target, seriallog);

			def content = """\
#! /bin/bash

# Exit bash script on error:
set -e

# Required environment setting
export TARGET=${target}
export SCHEDULER_ID=${env.BUILD_NUMBER}
export HYPERVISOR=${hypervisor}
""" + '''

export KERNCMDLINE="none"

echo "Check if target is back online..."
export TVERSION=$(ssh -o ConnectTimeout=10 -o ConnectionAttempts=6 $TARGET uname -r | sed "s/.*-rt[0-9]\\+-\\([0-9]\\+\\).*$/\\1/")

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


# Do not stop here. Set marker "target_reboot.failed" and wait some time to settle the hardware
if [ "$PASS" = "0" ]
then
	touch "target_reboot.failed"
fi
''';
			writeFile file:"${resultdir}/boottest.sh", text:content;
			sh "${content}";

			writeBootlog(seriallog, bootlog);

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

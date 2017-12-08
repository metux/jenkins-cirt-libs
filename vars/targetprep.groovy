#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

def call(Map global, String target, String kernel) {
	try {
		inputcheck.check(global);
		node(target) {
			dir("targetprep") {
				println("Run target preperation for ${kernel}");
				deleteDir();
				unstash(kernel.replaceAll('/','_'));

				/*
				 * Check if there are more or less than a single
				 * debian package in stash
				 */
				debfile = findFiles(glob: "compile/*.deb");
				if (debfile.size() != 1) {
					error message:"Not only a single deb file in stash. Abort";
				}

				/*
				 * clear the directory where test images, dtbs
				 * and initrds are stored
				 */
				sh 'rm -rf /boot/jenkins/*';

				/* Install package */
				sh "sudo dpkg -i ${debfile[0]}";

				script_content = """\
#! /bin/bash

# Exit bash script on error:
set -e

# Export required environment setting
SCHEDULER_ID=${env.BUILD_NUMBER}

"""+'''

# Copy linux image and initrd to /boot/jenkins directory. If
# /boot/jenkins/bzImage exists, a kexec is executed with the kernel
# that should be tested when leaving a runlevel
# (see /etc/rc.local on target).
find /boot/ -maxdepth 1 -regextype posix-extended -regex '^/boot/vmlinuz.*-'${SCHEDULER_ID}'(-.*|$)' -exec cp {} /boot/jenkins/bzImage \;
if [ -f /boot/initrd.img-*-${SCHEDULER_ID} ]
then
	cp /boot/initrd.img-*-${SCHEDULER_ID} /boot/jenkins/initrd
fi

# Purge the debian package. In case of a reboot the default (and
# properly working kernel version is booted)
sudo dpkg --purge ${DEB%%_*}

# Unpack devicetrees
find . -name dtbs-${SCHEDULER_ID}.tar.xz -exec tar xJf {} -C /boot/jenkins \;
'''
				writeFile file:"preperation.sh", text:script_content;
				sh "${script_content}";
			}
		}
	} catch(Exception ex) {
		println("targetprep ${kernel} on ${target} failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("targetprep ${kernel} on ${target} failed:");
	}
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

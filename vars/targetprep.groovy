#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT target preparation for boottest
 */

import de.linutronix.cirt.inputcheck;

def call(Map global, String target, String kernelstash) {
	try {
		inputcheck.check(global);
		node(target) {
			dir("targetprep") {
				println("Run target preperation for ${kernelstash}");
				deleteDir();
				unstash(kernelstash);

				/*
				 * Check if there are more or less than a single
				 * debian package in stash
				 */
				def debfile = findFiles(glob: "compile/*.deb");
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

				/*
				 * Copy Linux image and initrd to /boot/jenkins
				 * directory. If /boot/jenkins/bzImage exists, a
				 * kexec is executed with the kernel that should
				 * be tested when leaving a runlevel (see
				 * /etc/rc.local on the target).
				 */
				sh 'find /boot/ -maxdepth 1 -regextype posix-extended -regex \'^/boot/vmlinuz.*-'+env.BUILD_ID+'(-.*|$)\' -exec cp {} /boot/jenkins/bzImage \\;';
				sh 'find /boot/ -maxdepth 1 -regextype posix-extended -regex \'^/boot/initrd.img-.*-'+env.BUILD_ID+'(-.*|$)\' -exec cp {} /boot/jenkins/initrd \\;';

				/*
				 * Purge the debian package. In case of a reboot
				 * the default (and properly working kernel
				 * version is booted).
				 */
				def debname = "${debfile[0]}".replaceAll(/.*\//, '').replaceAll(/_.*/, '');
				sh "sudo dpkg --purge ${debname}";

				/* Unpack devicetrees */
				sh 'find . -name dtbs-'+env.BUILD_ID+'.tar.xz -exec tar xJf {} -C /boot/jenkins \\;';
			}
		}
	} catch(Exception ex) {
		println("targetprep ${kernelstash} on ${target} failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("targetprep ${kernelstash} on ${target} failed:");
	}
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}

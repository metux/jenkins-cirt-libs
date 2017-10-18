#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.libvirt;

private runner(Map global, String boottest) {
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
	boottestdir = "results/${kernel}";

	hypervisor = libvirt.getURI(target);
	helper.extraEnv("HYPERVISOR", hypervisor);
	println("URI = ${hypervisor}");

	dir(boottestdir) {
		lock(target) {
			helptext = "Reboot to Kernel build (${env.BUILD_TAG})";
			libvirt.wait4onlineTimeout(target, 120);

			targetprep(global, target, kernel);

			libvirt.offline(target, helptext);
			helper.runShellScript("boottest/reboottarget.sh");
			libvirt.online(target, helptext);

			cyclictests = helper.getEnv("CYCLICTESTS").split();
			cyclictest(global, target, cyclictests);
		}
	}
	archiveArtifacts(artifacts: "${boottestdir}/boottest/**",
			 fingerprint: true);
}

def call(Map global, String boottest) {
	try {
		inputcheck.check(global);
		runner(global, boottest);
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

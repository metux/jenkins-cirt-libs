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

				helper = new helper();
				helper.extraEnv("SCHEDULER_ID", env.BUILD_NUMBER);
				helper.runShellScript("targetprep/preperation.sh");
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

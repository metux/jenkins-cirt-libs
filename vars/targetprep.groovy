#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

def call(Map global, String target, String kernel) {
	node(target) {
		println("Run target preperation for ${kernel}");
		deleteDir();
		unstash(kernel.replaceAll('/','_'));

		helper = new helper();
		helper.extraEnv("SCHEDULER_ID", env.BUILD_NUMBER);
		helper.runShellScript("targetprep/preperation.sh");
	}
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

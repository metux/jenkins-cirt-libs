#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

private action(String cyclictest, helper helper) {
	dir('result/' + cyclictest) {
		helper.runShellScript("cyclictest/cyclictest.sh");
	}

	archiveArtifacts('result/**/histogram.*');
	stash(name: cyclictest.replaceAll('/','_'),
	      includes: 'result/**/histogram.*');
}

private runner(String cyclictest, String interval, String limit,
	       String loadgen) {
	helper = new helper();
	helper.extraEnv("INTERVAL", interval);
	helper.extraEnv("LIMIT", limit);
	if (loadgen?.trim()) {
		helper.extraEnv("LOADGEN", loadgen);
	}

	action(cyclictest, helper);
}

def call(String target, String cyclictest, String interval, String limit,
	 String loadgen) {

	println("cyclictest-runner: ${target} ${cyclictest} ${interval} ${limit}\n${loadgen}");

	node(target) {
		try {
			runner(cyclictest, interval, limit, loadgen);
		} catch(Exception ex) {
			println("cyclictest runner on ${target} failed:");
			println(ex.toString());
			println(ex.getMessage());
			println(ex.getStackTrace());
			error("cyclictest runner on ${target} failed.");
		}
	}
}

#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

private runner(Map global, String target, String cyclictest) {

	unstash(global.STASH_PRODENV);

	helper = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${cyclictest}.properties"];

	helper.add2environment(properties);

	loadgen = helper.getEnv("LOADGEN");
	loadgen?.trim();
	interval = helper.getEnv("INTERVAL");
	limit = helper.getEnv("LIMIT");

	println("cyclictest-runner: ${target} ${cyclictest} ${interval} ${limit}\n${loadgen}");

	config = helper.getEnv("CONFIG");
	overlay = helper.getEnv("OVERLAY");
	kernel = "${config}/${overlay}";
	cyclictestdir = "results/${kernel}/${target}/${cyclictest}";

	dir(cyclictestdir) {
		deleteDir();
		helper.runShellScript("cyclictest/cyclictest.sh");
	}

	archiveArtifacts("${cyclictestdir}/histogram.*");

	stash(name: cyclictest.replaceAll('/','_'),
	      includes: "${cyclictestdir}/histogram.*");
}

def call(Map global, String target, String cyclictest) {

	node(target) {
		try {
			dir("cyclictestRunner") {
				deleteDir();
				runner(global, target, cyclictest);
			}
		} catch(Exception ex) {
			println("cyclictest runner on ${target} failed:");
			println(ex.toString());
			println(ex.getMessage());
			println(ex.getStackTrace());
			error("cyclictest runner on ${target} failed.");
		}
	}
}

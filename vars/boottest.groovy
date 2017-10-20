#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

private boottestJob(Map global, String boottest) {
	return {
		println("Running boottest ${boottest}");
		boottestRunner(global, boottest);
	}
}

private runboottest(Map global, String[] boottests) {
	def stepsForParallel = [:];

	for (int i = 0; i < boottests.size(); i++) {
		def boottest = boottests.getAt(i);
		String[] properties = ["environment.properties",
				       "${boottest}.properties"];
		helper = new helper();
		helper.add2environment(properties);

		def jobName = "Boottest ${boottest}";
		stepsForParallel[jobName] = boottestJob(global, "${boottest}");
	};

	parallel(stepsForParallel);
}

def call(Map global) {
	String[] properties = ["environment.properties"];
	helper = new helper();

	deleteDir();
	unstash(global.STASH_PRODENV);
	helper.add2environment(properties);

	String[] boottests = helper.getEnv("BOOTTESTS_ALL").split();
	runboottest(global, boottests);
}

def call(String... params) {
        println params
        error("Unknown signature. Abort.");
}

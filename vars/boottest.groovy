#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

import de.linutronix.cirt.inputcheck;

private boottestJob(Map global, String boottest, String recipients) {
	//return {
		println("Running boottest ${boottest}");
		dir ("../") {
			boottestRunner(global, boottest, recipients);
		}
	//}
}

private runboottest(Map global, String[] boottests, String recipients) {
	//def stepsForParallel = [:];

	for (int i = 0; i < boottests.size(); i++) {
		def boottest = boottests.getAt(i);

		//def jobName = "Boottest ${boottest}";
		//stepsForParallel[jobName] =
		boottestJob(global, "${boottest}", recipients);
	};

	//parallel(stepsForParallel);
}

def call(Map global, String[] boottests, String recipients) {
	try {
		inputcheck.check(global);
		node('master') {
			dir("boottest") {
				deleteDir();
				if (boottests == null) {
					return;
				}
				runboottest(global, boottests, recipients);
			}
		}
	} catch(Exception ex) {
		println("boottest failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("boottest failed.");
	}
}

def call(Map global) {
	call(global, null);
}

def call(String... params) {
        println params
	error("Unknown signature. Abort.");
}

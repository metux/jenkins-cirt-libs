#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

import de.linutronix.cirt.inputcheck;

private boottestJob(Map global, String boottest) {
	//return {
		println("Running boottest ${boottest}");
		dir ("../") {
			boottestRunner(global, boottest);
		}
	//}
}

private runboottest(Map global, String[] boottests) {
	def stepsForParallel = [:];

	for (int i = 0; i < boottests.size(); i++) {
		def boottest = boottests.getAt(i);

		//def jobName = "Boottest ${boottest}";
		//stepsForParallel[jobName] =
		boottestJob(global, "${boottest}");
	};

	//parallel(stepsForParallel);
}

def call(Map global, String[] boottests) {
	try {
		inputcheck.check(global);
		node('master') {
			dir("boottest") {
				deleteDir();
				if (boottests == null)
					return;
				runboottest(global, boottests);
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

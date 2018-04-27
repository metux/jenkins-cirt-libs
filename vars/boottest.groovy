#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT boottest
 */

import de.linutronix.cirt.VarNotSetException;
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
		boottest = null;
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
		if (ex instanceof VarNotSetException) {
			throw ex;
		}
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

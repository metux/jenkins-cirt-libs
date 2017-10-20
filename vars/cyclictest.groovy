#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library cyclictest handling
 */

package de.linutronix.cirt;

def call(Map global, String target, String[] cyclictests) {
	node('master') {
		for (int i = 0; i < cyclictests.size(); i++) {
			ct = cyclictests[i];

			String[] properties = ["environment.properties",
					       "${ct}.properties"];
			helper = new helper();
			deleteDir();
			unstash(global.STASH_PRODENV);
			helper.add2environment(properties);

			interval = helper.getEnv("INTERVAL");
			limit = helper.getEnv("LIMIT");
			loadgen = helper.getEnv("LOADGEN");
			cyclictestRunner(target, ct, interval, limit, loadgen);
		}
	}
}

def call(String... params) {
        println params
        error("Unknown signature. Abort.");
}

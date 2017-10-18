#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library cyclictest handling
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

def call(Map global, String target, String[] cyclictests) {
	try {
                inputcheck.check(global);
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
				cyclictestRunner(target, ct, interval, limit,
						 loadgen);
			}
		}
	} catch(Exception ex) {
                println("cyclictest on ${target} failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("cyclictest on ${target} failed.");
        }
}

def call(String... params) {
        println params
        error("Unknown signature. Abort.");
}

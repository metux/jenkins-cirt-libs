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
			dir ("cyclictest") {
				deleteDir();
				for (int i = 0; i < cyclictests.size(); i++) {
					ct = cyclictests[i];

					/*
					 * Cyclictest runner is executed on target;
					 * workspace directory doesn't has to be changed
					 */
					cyclictestRunner(global, target, ct);
				}
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

#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library junit helper
 */

def call(String filename) {
	try {
		result = junit(filename)

		if (result.failCount >= 1) {
			return 'UNSTABLE';
		} else {
			return 'SUCCESS';
		}
	} catch(Exception ex) {
		println("junit_result() failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("junit_result() failed.");
	}
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

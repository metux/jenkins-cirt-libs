#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT common input checks
 */

def call(Map global) {
	node ('master') {
		if (!global.STASH_PATCHES?.trim()) {
			error("variable STASH_PATCHES not set.");
		}

		if (!global.STASH_PRODENV?.trim()) {
			error("variable STASH_PRODENV not set.");
		}

		if (!global.STASH_RAWENV?.trim()) {
			error("variable STASH_RAWENV not set.");
		}

		if (!global.STASH_COMPILECONF?.trim()) {
			error("variable STASH_COMPILECONF not set.");
		}

		if (!global.TESTDESCRIPTION_REPO?.trim()) {
			error("variable TESTDESCRIPTION_REPO not set.");
		}

		if (!global.GUI_TESTDESCR_BRANCH?.trim()) {
			error("variable GUI_TESTDESCR_BRANCH not set.");
		}
	}
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

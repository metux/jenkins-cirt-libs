/* -*- mode: groovy; -*-
 * CI-RT common input checks
 */

package de.linutronix.cirt;

static def check(Map global) {
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
}

static def check(String branch, Map global) {
        if (!branch?.trim()) {
                error("param GUI_TESTDESCR_BRANCH not given.");
        }

	check(global);
}

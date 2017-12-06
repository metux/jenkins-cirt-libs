#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

private String prepareGlobalEnv(String globalenv, String commit) {
	/* no need to set PUBLICREPO if found earlier */
	def m = globalenv =~ /\s*PUBLICREPO\s*=.*/
	if (m.count) {
		publicrepo = '';
	} else {
		publicrepo = 'PUBLICREPO=true';
	}
	m = null;

	/*
	 * determine commit to build:
	 * 1. commit is set by gui
	 * 2. commit is set by test
	 * 3. fallback to branch
	 *
	 * Therefore, remove COMMIT from environment and
	 * set to an explicit value later on.
	 */

	if (commit.trim()) {
		/* remove COMMIT from environment */
		globalenv = globalenv - ~/\s*COMMIT\s*=.*/
	} else {
		commit = globalenv =~ /\s*COMMIT\s*=.*/
		if (commit.count) {
			commit -= ~/\s*COMMIT\s*=/

			/* remove COMMIT from environment */
			globalenv = globalenv - ~/\s*COMMIT\s*=.*/
		} else {
			branch = globalenv =~ /\s*BRANCH\s*=.*/
			commit = branch[0] - ~/\s*BRANCH\s*=/
		}
	}

	globalenv += """
COMMIT=${commit}
GIT_CHECKOUT=${commit}
SCHEDULER_ID=${env.BUILD_NUMBER}
ENV_ID=${BUILD_NUMBER}
CI_RT_URL=${env.BUILD_URL}
${publicrepo}
"""

	/* remove all empty lines and whitespaces around [=] */
	globalenv = globalenv.replaceAll(/(?m)^\s*\n/, "");
	globalenv = globalenv.replaceAll(/\s*=\s*/, "=");

	return handleLists(globalenv);
}

private buildGlobalEnv(String commit) {
	try {
		findFiles(glob: '**/.properties')
		error("found .properties files in testdescription. CIRTbuildenv failed.");
	} catch(Exception ex) {
		println("clean workspace.");
	}

	def globalenv = readFile("env/global");
	globalenv = prepareGlobalEnv(globalenv, commit);

	writeFile(file:"environment.properties", text:globalenv);
}

private buildCompileEnv() {
	helper = new helper();
	handleLists(helper);

	String[] boottests = helper.getEnv("BOOTTESTS_ALL").split();
	String[] configs = helper.getEnv("CONFIGS").split();
	String[] overlays = helper.getEnv("OVERLAYS").split();

	if (boottests) {
		CIRTbuildenvCompileBoot(configs, overlays);
		CIRTbuildenvCyclictest(boottests);
	}
}

private handleLists(helper helper) {
	helper.list2prop("env/compile.list", "CONFIGS", "environment.properties");
	helper.list2prop("env/overlay.list", "OVERLAYS", "environment.properties");
	helper.list2prop("env/boottest.list", "BOOTTESTS_ALL", "environment.properties");
	helper.list2prop("env/email.list", "RECIPIENTS", "environment.properties");

	String[] properties = ["environment.properties"];
	helper.add2environment(properties);
	helper.runShellScript("environment/arch_env_magic.py");
}

private buildEnv(String commit) {
	buildGlobalEnv(commit);
	buildCompileEnv();
}

def call(String commit, Map global) {
	inputcheck.check(global);
	try {
		node('master') {
			dir('environment') {
				deleteDir();
				unstash(global.STASH_RAWENV);

				buildEnv(commit);

				/*
				 * Stash *.properties files; directory
				 * hierarchy doesn't change
				 */
				stash(includes: '**/*.properties',
				      name: global.STASH_PRODENV);
				stash(includes: 'patches/**',
				      name: global.STASH_PATCHES);
				stash(includes: 'compile/configs/**, compile/overlays/**',
				      name: global.STASH_COMPILECONF);
			}
			archiveArtifacts(artifacts: 'environment/**/*.properties, environment/patches/**, environment/compile/configs/**, environment/compile/overlays/**',
					 fingerprint: true);
		}
	} catch(Exception ex) {
                println("CIRTbuildenv failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("CIRTbuildenv failed.");
        }
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

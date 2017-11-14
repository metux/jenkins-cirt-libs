#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

private buildEnv(String commit) {
	String[] properties = ["environment.properties"];
	helper = new helper();

	helper.extraEnv("SCHEDULER_ID", "${env.BUILD_NUMBER}");
	helper.extraEnv("CI_RT_URL", "${env.BUILD_URL}");
	helper.runShellScript("environment/base.sh");
	helper.add2environment(properties);

	helper.extraEnv("COMMIT", "${commit}");
	helper.runShellScript("environment/setcommit.sh");
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

def call(Integer schedId, String commit, Map global) {
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
			archiveArtifacts(artifacts: '**/*.properties, environment/patches/**, environment/compile/configs/**, environment/compile/overlays/**',
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

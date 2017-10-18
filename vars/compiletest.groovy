#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

private compileJob(Map global, String config, String overlay,
		   String repo, String branch) {
	return {
		def compiledir = "results/${config}/${overlay}";
		println("Repository ${repo} ${branch}");
		println("Compile Job ${config} ${overlay}");
		deleteDir()
		dir(compiledir) {
			compiletestRunner(global, repo, branch,
					  config, overlay);
		}
		archiveArtifacts(artifacts: "${compiledir}/compile/**",
                                 fingerprint: true);
	}
}

private compile(Map global, Map environment) {
	def stepsForParallel = [:];

	configs = environment['CONFIGS'].split();
	overlays = environment['OVERLAYS'].split();

	for (int i = 0; i < configs.size(); i++) {
		for (int j = 0; j < overlays.size(); j++) {
			def jobName = "Build ${configs.getAt(i)} ${overlays.getAt(j)}";
			stepsForParallel[jobName] =
				compileJob(global,
					   "${configs.getAt(i)}",
					   "${overlays.getAt(j)}",
					   environment['GITREPO'],
					   environment['GIT_CHECKOUT']);
		}
	}

	parallel(stepsForParallel);
}

def call(Map global) {
	try {
		inputcheck.check(global);
		unstash(global.STASH_PRODENV);
		String[] properties = ["environment.properties"];
		helper = new helper();

		helper.add2environment(properties);
		try {
			environment = helper.getEnv();
			compile(global, environment);
		}
		catch (java.lang.NullPointerException e) {
			error("CONFIGS, OVERLAYS, GITREPO or GIT_CHECKOUT not set. Abort.");
		}
	} catch(Exception ex) {
                println("compiletest failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("compiletest failed.");
        }
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

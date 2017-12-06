#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

private compileJob(Map global, String config, String overlay,
		   String repo, String branch) {
	/* fileExists is a relative query! */
	configbootprop = "compile/env/${config.replaceAll('/','_')}_${overlay}.properties";
	boot = fileExists "${configbootprop}";

	if (boot) {
		String[] boottestprops = [configbootprop];
		String[] boottests;
		helper = new helper();

		helper.add2environment(boottestprops);
		try {
			helper.showEnv();
			boottests = helper.getEnv("BOOTTESTS").split();
		} catch (java.lang.NullPointerException e) {
			boottests = [];
		}

		return {
			stage ("compile ${repo} ${branch} ${config} ${overlay}") {
				/*
				 * Subprocesses needs to be started in
				 * WORKSPACE!
				 */
				dir("../") {
					compiletestRunner(global, repo, branch,
							  config, overlay);
				}
			}
			stage ("Boottests ${config} ${overlay}") {
				node('master') {
					/*
					 * boottest is executed on
					 * another node, workspace
					 * doesn't has to be changed.
					 */
					boottest(global, boottests);
				}
			}
		}
	} else {
		return {
			stage ("compile ${repo} ${branch} ${config} ${overlay}") {
				/*
				 * Subprocesses needs to be started in
				 * WORKSPACE!
				 */
				dir("../") {
					compiletestRunner(global, repo, branch,
							  config, overlay);
				}
			}
		}
	}
}

private compile(Map global, String[] configs, String[] overlays,
		String gitrepo, String gitcheckout) {
	def stepsForParallel = [:];

	for (int i = 0; i < configs.size(); i++) {
		for (int j = 0; j < overlays.size(); j++) {
			def jobName = "Build ${configs.getAt(i)} ${overlays.getAt(j)}";
			stepsForParallel[jobName] =
				compileJob(global,
					   "${configs.getAt(i)}",
					   "${overlays.getAt(j)}",
					   gitrepo,
					   gitcheckout);
		}
	}

	parallel(stepsForParallel);
}

def call(Map global) {
	try {
		inputcheck.check(global);
		dir("compiletest") {
			deleteDir();
			unstash(global.STASH_PRODENV);
			String[] properties = ["environment.properties"];
			helper = new helper();

			helper.add2environment(properties);
			/*
			 * TODO: Check, if properties file has all
			 * information will be later moved into
			 * environment verification
			 */
			try {
				environment = helper.getEnv();
				configs = environment['CONFIGS'].split();
				overlays = environment['OVERLAYS'].split();
				gitrepo = environment['GITREPO'];
				gitcheckout = environment['GIT_CHECKOUT'];
			}
			/* Catches not set environment Parameters */
			catch (java.lang.NullPointerException e) {
				println(e.toString());
				println(e.getMessage());
				println(e.getStackTrace());
				error("CONFIGS, OVERLAYS, GITREPO or GIT_CHECKOUT not set. Abort.");
			}
			compile(global, configs, overlays, gitrepo, gitcheckout);
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

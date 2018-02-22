#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

import de.linutronix.cirt.helper;
import de.linutronix.cirt.inputcheck;

private compileJob(Map global, String config, String overlay,
		   String repo, String branch, String recipients) {
	/* fileExists is a relative query! */
	def configbootprop = "compile/env/${config.replaceAll('/','_')}_${overlay}.properties";
	def boot = fileExists "${configbootprop}";

	if (boot) {
		String[] boottestprops = [configbootprop];
		String[] boottests;
		def h = new helper();

		h.add2environment(boottestprops);
		try {
			h.showEnv();
			boottests = h.getEnv("BOOTTESTS").split();
		} catch (java.lang.NullPointerException e) {
			boottests = [];
		}

		return {
			def compileresult = ''
			stage ("compile ${repo} ${branch} ${config} ${overlay}") {
				/*
				 * Subprocesses needs to be started in
				 * WORKSPACE!
				 */
				dir("../") {
					compileresult = compiletestRunner(global, repo, branch,
									  config, overlay,
									  recipients);
				}
			}
			stage ("Boottests ${config} ${overlay}") {
				node('master') {
					/*
					 * boottest is executed on
					 * another node, workspace
					 * doesn't has to be changed.
					 */
					if (compileresult == 'SUCCESS') {
						boottest(global, boottests,
							 recipients);
					} else {
						println ("Boottest skipped due to previous failure");
					}
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
							  config, overlay,
							  recipients);
				}
			}
		}
	}
}

private compile(Map global, String[] configs, String[] overlays,
		String gitrepo, String gitcheckout, String recipients) {
	def stepsForParallel = [:];

	for (int i = 0; i < configs.size(); i++) {
		for (int j = 0; j < overlays.size(); j++) {
			def jobName = "Build ${configs.getAt(i)} ${overlays.getAt(j)}";
			stepsForParallel[jobName] =
				compileJob(global,
					   "${configs.getAt(i)}",
					   "${overlays.getAt(j)}",
					   gitrepo,
					   gitcheckout,
					   recipients);
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
			def h = new helper();
			def configs = "";
			def overlays = "";
			def gitrepo = "";
			def gitcheckout = "";
			def recipients = "";

			h.add2environment(properties);
			/*
			 * TODO: Check, if properties file has all
			 * information will be later moved into
			 * environment verification
			 */
			try {
				def environment = h.getEnv();
				configs = environment['CONFIGS'].split();
				overlays = environment['OVERLAYS'].split();
				gitrepo = environment['GITREPO'];
				gitcheckout = environment['GIT_CHECKOUT'];
				recipients = environment['RECIPIENTS'].trim();
			}
			/* Catches not set environment Parameters */
			catch (java.lang.NullPointerException e) {
				println(e.toString());
				println(e.getMessage());
				println(e.getStackTrace());
				error("CONFIGS, OVERLAYS, GITREPO, GIT_CHECKOUT or RECIPIENTS not set. Abort.");
			}
			compile(global, configs, overlays, gitrepo,
				gitcheckout, recipients);
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

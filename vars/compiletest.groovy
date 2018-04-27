#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT compiletest
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;
import de.linutronix.cirt.inputcheck;
import de.linutronix.lib4lib.safesplit;

private compileJob(Map global, String config, String overlay,
		   String repo, String branch, String recipients) {
	/* fileExists is a relative query! */
	def configbootprop = "compile/env/${config.replaceAll('/','_')}_${overlay}.properties";
	def boot = fileExists "${configbootprop}";

	if (boot) {
		boot = null;

		String[] boottestprops = [configbootprop];
		configbootprop = null;

		String[] boottests;
		def h = new helper();

		h.add2environment(boottestprops);
		h.showEnv();
		boottests = safesplit.split(h.getVar("BOOTTESTS", " "));
		h = null;

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
					compileresult = null;
					boottests = null;
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
			jobName = null;
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

			configs = h.getVar('CONFIGS').split();
			overlays = h.getVar('OVERLAYS').split();
			gitrepo = h.getVar('GITREPO');
			gitcheckout = h.getVar('GIT_CHECKOUT');
			recipients = h.getVar('RECIPIENTS');

			h = null;

			compile(global, configs, overlays, gitrepo,
				gitcheckout, recipients);
		}
	} catch(Exception ex) {
		if (ex instanceof VarNotSetException) {
                        throw ex;
                }
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

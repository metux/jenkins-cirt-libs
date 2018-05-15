#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT job to feed results into database
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;
import de.linutronix.lib4lib.safesplit;

import hudson.AbortException

def collectCompiletests(configs, overlays, unstashDir, helper) {
	def config;
	def overlay;
	def compiledir;
	def firstRun = true;
	def lastRun;

	def script_content = libraryResource('de/linutronix/cirt/feedDatabase.py');
	writeFile file:unstashDir + "/feedDatabase", text:script_content;

	for (int i = 0; i < configs.size(); i++) {
		config = configs.getAt(i);
		for (int j = 0; j < overlays.size(); j++) {
			overlay = overlays.getAt(j);
			compiledir = "results/${config}/${overlay}";
			dir(unstashDir) {
				try {
					unstash(compiledir.replaceAll('/','_'));
				} catch (AbortException ex) {
					/* catch non existing stash */
					println("Feeddatabase Info only: "+ex.toString());
					println("Feeddatabase Info only: "+ex.getMessage());
					continue;
				}

				String gittagsprop = compiledir + "/compile/gittags.properties";
				String[] properties = [gittagsprop]
				helper.add2environment(properties);

				collectBoottests(config, overlay, helper);
				lastRun = (i == configs.size() - 1 && j == overlays.size() - 1);

				/*
				 * compiletest may fail in configuration phase
				 * and therefore some files like compile
				 * script or defconfig do not exist.
				 * Skip feed database for now, until the
				 * python script fill up some good defaults
				 * for mandatory database row values.
				 * pyjutest.xml is the last created file in
				 * a compile test, test for it to decide
				 * whether database feeding needs to be done.
				 *
				 * Skiping stash do not work here since some
				 * information files like gittas.properties
				 * are needed in user notification.
				 */
				if (fileExists(compiledir + "/compile/pyjutest.xml")) {
					runPythonScript(firstRun, lastRun,
							unstashDir, compiledir,
							helper, config,
							overlay);
					firstRun = false
				} else {
					println("Feeddatabase Info only: No JUnit result file exist for ${config}/${overlay}");
					println("Feeddatabase Info only: Skip database feed.");
				}
			}
		}
	}
}

def runPythonScript(firstRun, lastRun, unstashDir, compiledir, helper, config, overlay) {
	def pFirstRun;
	def pLastRun;
	if (firstRun) {
		pFirstRun = "first_run";
	} else {
		pFirstRun = "false";
	}
	if (lastRun) {
		pLastRun = "last_run";
	} else {
		pLastRun = "false"
	}
	sh("python3 feedDatabase feedDatabase/${unstashDir}/${compiledir}\
		--buildnumber ${env.BUILD_NUMBER}\
		--workspace ${env.WORKSPACE}\
		--overlay ${overlay}\
		--config ${config}\
		--git_branch ${env.GIT_BRANCH}\
		--git_commit ${env.GIT_COMMIT}\
		--gitrepo ${helper.getVar('GITREPO')}\
		--publicrepo ${helper.getVar('PUBLICREPO')}\
		--httprepo ${helper.getVar('HTTPREPO', " ")}\
		--tags_commit ${helper.getVar('TAGS_COMMIT')}\
		--tags_name ${helper.getVar('TAGS_NAME')}\
		--branch ${helper.getVar('BRANCH', " ")}\
		--entryowner ${helper.getVar('ENTRYOWNER')}\
		--first_run ${pFirstRun} --last_run ${pLastRun}")
}

def collectBoottests(config, overlay, helper) {
	def boottest;
	def kernel;

	String configbootprop = "../compile/env/${config.replaceAll('/','_')}_${overlay}.properties";

	def boot = fileExists "${configbootprop}";
	if (boot) {
		String[] properties = [configbootprop]
		helper.add2environment(properties);

		def boottests = safesplit.split(helper.getVar("BOOTTESTS", " "));
		for (int k = 0; k < boottests?.size(); k++) {
			boottest = boottests.getAt(k)
			try {
				unstash(boottest.replaceAll('/','_'));
			} catch (AbortException ex) {
				/* catch non existing stash */
				println("Feeddatabase Info only: "+ex.toString());
				println("Feeddatabase Info only: "+ex.getMessage());
				continue;
			}
			properties = ["../${boottest}.properties"];
			helper.add2environment(properties);

			kernel = "${config}/${overlay}";
			collectCyclictests(kernel, helper);
		}
	}
}

def collectCyclictests(kernel, helper) {
	def cyclictest;
	def cyclictestdir;

	def target = helper.getVar('TARGET', " ");
	def cyclictests = safesplit.split(helper.getVar("CYCLICTESTS", " "));
	for (int l = 0; l < cyclictests?.size(); l++) {
		cyclictest = cyclictests.getAt(l)
		cyclictestdir = "results/${kernel}/${target}/${cyclictest}";
		try {
			unstash(cyclictestdir.replaceAll('/','_'));
		} catch (AbortException ex) {
			/* catch non existing stash */
			println("Feeddatabase Info only: "+ex.toString());
			println("Feeddatabase Info only: "+ex.getMessage());
		}
	}
}

def call(Map global) {
	try {
		println("feeding database");
		dir("feedDatabase") {
			deleteDir();

			unstash(global.STASH_PRODENV);
			String[] properties = ["environment.properties"];
			h = new helper();

			h.add2environment(properties);

			def unstashDir = "db_unstash";
			def configs = safesplit.split(h.getVar('CONFIGS'));
			def overlays = safesplit.split(h.getVar('OVERLAYS'));

			println("collect all results");
			collectCompiletests(configs, overlays, unstashDir, h);
		}
	} catch(Exception ex) {
		if (ex instanceof VarNotSetException) {
                        throw ex;
                }
		println("feeddatabase failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("feeddatabase failed.");
	}
}

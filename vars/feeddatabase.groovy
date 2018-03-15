#!/usr/bin/env groovy
/*
 * CI-RT job to feed results into database
 */

import de.linutronix.cirt.helper;
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
				runPythonScript(firstRun, lastRun, unstashDir, compiledir, helper, config, overlay);
				firstRun = false
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
		--gitrepo ${helper.getEnv('GITREPO')}\
		--publicrepo ${helper.getEnv('PUBLICREPO')}\
		--httprepo ${helper.getEnv('HTTPREPO')}\
		--tags_commit ${helper.getEnv('TAGS_COMMIT')}\
		--tags_name ${helper.getEnv('TAGS_NAME')}\
		--branch ${helper.getEnv('BRANCH')}\
		--entryowner ${helper.getEnv('ENTRYOWNER')}\
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

		def boottests = helper.getEnv("BOOTTESTS").split();
		for (int k = 0; k < boottests.size(); k++) {
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

	def target = helper.getEnv('TARGET');
	def cyclictests = helper.getEnv("CYCLICTESTS").split();
	for (int l = 0; l < cyclictests.size(); l++) {
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
			def configs = h.getEnv('CONFIGS').split();
			def overlays = h.getEnv('OVERLAYS').split();

			println("collect all results");
			collectCompiletests(configs, overlays, unstashDir, h);
		}
	} catch(Exception ex) {
		println("feeddatabase failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("feeddatabase failed.");
	}
}

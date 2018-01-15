#!/usr/bin/groovy

import de.linutronix.cirt.helper;

def collectCompiletests(configs, overlays, unstashDir, helper) {
	def config;
	def overlay;
	def compiledir;
	def firstRun = true;
	def lastRun;

	script_content = libraryResource('de/linutronix/cirt/feedDatabase.py');
	writeFile file:unstashDir + "/feedDatabase", text:script_content;

	for (int i = 0; i < configs.size(); i++) {
		config = configs.getAt(i);
		for (int j = 0; j < overlays.size(); j++) {
			overlay = overlays.getAt(j);
			compiledir = "results/${config}/${overlay}";
			dir(unstashDir) {
				collectBoottests(compiledir, config, overlay, helper);
				lastRun = (i == configs.size() - 1 && j == overlays.size() - 1);
				runPythonScript(firstRun, lastRun, unstashDir, compiledir, helper);
			}
		}
	}
}

def runPythonScript(firstRun, lastRun, unstashDir, compiledir, helper) {
	def pFirstRun;
	def pLastRun;
	if (firstRun) {
		pFirstRun = "first_run";
		firstRun = false
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
		--overlay ${helper.getEnv('OVERLAY')}\
		--config ${helper.getEnv('CONFIG')}\
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

def collectBoottests(compiledir, config, overlay, helper) {
	def boottest;
	def kernel;
	unstash(compiledir.replaceAll('/','_'));
	String configbootprop = "../compile/env/${config.replaceAll('/','_')}_${overlay}.properties";
	String gittagsprop = compiledir + "/compile/gittags.properties";
	def boot = fileExists "${configbootprop}";
	if (boot) {
		String[] properties = [configbootprop, gittagsprop]
		helper.add2environment(properties);
		def boottests = helper.getEnv("BOOTTESTS").split();
		for (int k = 0; k < boottests.size(); k++) {
			boottest = boottests.getAt(k)
			kernel = "${config}/${overlay}";
			collectCyclictests(boottest, kernel, helper);
		}
	}
}

def collectCyclictests(boottest, kernel, helper) {
	def cyclictest;
	def cyclictestdir;
	unstash(boottest.replaceAll('/','_'));
	String[] properties = ["../${boottest}.properties"];
	helper.add2environment(properties);
	def target = helper.getEnv('TARGET');
	def cyclictests = helper.getEnv("CYCLICTESTS").split();
	for (int l = 0; l < cyclictests.size(); l++) {
		cyclictest = cyclictests.getAt(l)
		cyclictestdir = "results/${kernel}/${target}/${cyclictest}";
		unstash(cyclictest.replaceAll('/','_'));
	}
}

def call(Map global) {
	println("feeding database");
	dir("feedDatabase") {
		deleteDir();

		unstash(global.STASH_PRODENV);
		String[] properties = ["environment.properties"];
		h = new helper();

		h.add2environment(properties);

		def unstashDir = "db_unstash"
		def configs = h.getEnv('CONFIGS').split();
		def overlays = h.getEnv('OVERLAYS').split();

		println("collect all results");
		collectCompiletests(configs, overlays, unstashDir, h);
	}
}

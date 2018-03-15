#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT cyclictest
 */

import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.helper;

private failnotify(Map global, helper h, String target,
		   String cyclictestdir, String recipients)
{
	def loadgen = h.getEnv("LOADGEN")?.trim();
	def interval = h.getEnv("INTERVAL");
	def limit = h.getEnv("LIMIT");
	def repo = h.getEnv("GITREPO");
	def branch = h.getEnv("BRANCH");
	def config = h.getEnv("CONFIG");
	def overlay = h.getEnv("OVERLAY");
	def results = "results/${config}/${overlay}";

	dir("failurenotification") {
		deleteDir();
		unstash(results.replaceAll('/','_'));

		/*
		 * Specifying a relative path starting with "../" does not
		 * work in notify attachments.
		 * Copy cyclictest results into this folder.
		 */
		sh("cp ../${cyclictestdir}/histogram.* .");

		def gittags = readFile "${results}/compile/gittags.properties";
		gittags = gittags.replaceAll(/(?m)^\s*\n/, "");

		notify("${recipients}",
		       "cyclictest-runner failed!",
		       "cyclictestRunner",
		       "histogram.*",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay, "target": target,
			"limit": limit, "interval": interval,
			"loadgen": loadgen, "cyclictestdir": cyclictestdir,
			"gittags": gittags]);
	}
}

private parse_results(Map global, String target, String ct, String recipients)
{
	unstash(global.STASH_PRODENV);
	def h = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${ct}.properties"];
	h.add2environment(properties);
	properties = null;
	def config = h.getEnv("CONFIG");
	def overlay = h.getEnv("OVERLAY");
	def kernel = "${config}/${overlay}";
	config = null;
	overlay = null;

	def cyclictestdir = "results/${kernel}/${target}/${ct}";
	unstash(cyclictestdir.replaceAll('/','_'));

	script_content = libraryResource('de/linutronix/cirt/cyclictest/cyclictest2xml.py');
	writeFile file:"collect", text:script_content;
	sh("python3 collect ${ct} ${cyclictestdir} \
	    --entryowner ${h.getEnv('ENTRYOWNER')}			\
	    --duration ${h.getEnv('DURATION')}				\
	    --interval ${h.getEnv('INTERVAL')}				\
	    --limit ${h.getEnv('LIMIT')}")

	def result = junit_result("${cyclictestdir}/pyjutest.xml");
	archiveArtifacts("${cyclictestdir}/pyjutest.xml");
	stash(name: cyclictestdir.replaceAll('/','_'),
	      includes: "${cyclictestdir}/histogram.*," +
			"${cyclictestdir}/pyjutest.xml");

	if (result == 'UNSTABLE') {
		failnotify(global, h, target, cyclictestdir, recipients);
	}
}

def call(Map global, String target, String[] cyclictests, String recipients) {
	try {
                inputcheck.check(global);
		def results = [];
		node('master') {
			dir ("cyclictest") {
				deleteDir();
				for (int i = 0; i < cyclictests.size(); i++) {
					def ct = cyclictests[i];

					/*
					 * Cyclictest runner is executed on target;
					 * workspace directory doesn't has to be changed
					 */
					cyclictestRunner(global, target, ct);
					results << parse_results(global, target, ct,
								 recipients);
				}
			}
		}
		if (results.contains('UNSTABLE')) {
			return 'UNSTABLE';
		} else {
			return 'SUCCESS';
		}
	} catch(Exception ex) {
                println("cyclictest on ${target} failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("cyclictest on ${target} failed.");
        }
}

def call(String... params) {
        println params
        error("Unknown signature. Abort.");
}

#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT cyclictest runner
 */

import de.linutronix.cirt.helper;

private runner(Map global, String target, String cyclictest) {

	unstash(global.STASH_PRODENV);

	def h = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${cyclictest}.properties"];

	h.add2environment(properties);
	properties = null;

	def loadgen = h.getEnv("LOADGEN")?.trim();
	def interval = h.getVar("INTERVAL");
	def limit = h.getVar("LIMIT");
	def duration = h.getVar("DURATION");
	def config = h.getVar("CONFIG");
	def overlay = h.getVar("OVERLAY");
	h = null;

	println("cyclictest-runner: ${target} ${cyclictest} ${interval} ${limit}\n${loadgen}");

	def kernel = "${config}/${overlay}";
	def cyclictestdir = "results/${kernel}/${target}/${cyclictest}";
	kernel = null

	dir(cyclictestdir) {
		deleteDir();
		def content = """#! /bin/bash

# Exit bash script on error:
set -e

${loadgen ?: 'true'} &

# Output needs to be available in Jenkins as well - use tee
sudo cyclictest -q -m -Sp99 -D${duration} -i${interval} -h${limit} -b${limit} --notrace 2> >(tee histogram.log >&2) | tee histogram.dat
""";
		writeFile file:"histogram.sh", text:content;
		content = null;
		sh ". histogram.sh";
	}

	archiveArtifacts("${cyclictestdir}/histogram.*");

	stash(name: cyclictestdir.replaceAll('/','_'),
	      includes: "${cyclictestdir}/histogram.*");

	/*
	 * no mail notification here since test examination need
	 * to run on master.
	 * See cyclictest.groovy.
	 */
}

def call(Map global, String target, String cyclictest) {

	node(target) {
		try {
			dir("cyclictestRunner") {
				deleteDir();
				runner(global, target, cyclictest);
			}
		} catch(Exception ex) {
			println("cyclictest runner on ${target} failed:");
			println(ex.toString());
			println(ex.getMessage());
			println(ex.getStackTrace());
			error("cyclictest runner on ${target} failed.");
		}
	}
}

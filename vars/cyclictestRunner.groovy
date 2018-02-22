#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

import de.linutronix.cirt.helper;

private runner(Map global, String target, String cyclictest) {

	unstash(global.STASH_PRODENV);

	def h = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${cyclictest}.properties"];

	h.add2environment(properties);

	def loadgen = h.getEnv("LOADGEN");
	loadgen?.trim();
	def interval = h.getEnv("INTERVAL");
	def limit = h.getEnv("LIMIT");
	def duration = h.getEnv("DURATION");

	println("cyclictest-runner: ${target} ${cyclictest} ${interval} ${limit}\n${loadgen}");

	def config = h.getEnv("CONFIG");
	def overlay = h.getEnv("OVERLAY");
	def kernel = "${config}/${overlay}";
	def cyclictestdir = "results/${kernel}/${target}/${cyclictest}";

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

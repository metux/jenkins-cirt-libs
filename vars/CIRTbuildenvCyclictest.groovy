#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

private prepareEnv(String boottest) {
	String[] properties = ["${boottest}.properties"];
	helper = new helper();

	sh("cp ${boottest} ${boottest}.properties");
	helper.add2environment(properties);
	helper.list2prop(helper.getEnv("CYCLICTEST"), "CYCLICTESTS",
			 "${boottest}.properties");
	helper.add2environment(properties);

	cyclictests = helper.getEnv("CYCLICTESTS").split();
	for (int i = 0; i < cyclictests.size(); i++) {
		cyclictest = cyclictests.getAt(i);
		sh("cp ${cyclictest} ${cyclictest}.properties");
	}
}

def call(String[] boottests) {
	for (int i = 0; i < boottests.size(); i++) {
		prepareEnv(boottests.getAt(i));
	}
}

def call(Integer schedId, String[] boottests) {
	node('master') {
		stage("environment-cyclictest") {
			deleteDir();
			unstash('rawenvironment');
			for (int i = 0; i < boottests.size(); i++) {
				prepareEnv(boottests.getAt(i));
				archiveArtifacts(artifacts: "${boottests.getAt(i)}.properties",
						 allowEmptyArchive: true,
						 fingerprint: true);
			}
		}
	}
}

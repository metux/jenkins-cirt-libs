#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;

private buildEnv(String commit) {
	try {
		findFiles(glob: '**/.properties')
		error("found .properties files in testdescription. CIRTbuildenv failed.");
	} catch(Exception ex) {
		println("clean workspace.");
	}

	sh """\
#! /bin/bash

# Exit bash script on error:
set -e

cp env/global environment.properties

echo "SCHEDULER_ID=${env.BUILD_NUMBER}" >> environment.properties
echo "ENV_ID=${BUILD_NUMBER}" >> environment.properties
echo "CI_RT_URL=${env.BUILD_URL}" >> environment.properties

# Insert PUBLICREPO if it is not set (default value is true)
grep -q '^PUBLICREPO' environment.properties || echo "PUBLICREPO=true" >> environment.properties

# If parameter COMMIT is not empty (only whitespace means empty) overwrite existing COMMIT entry
# or add it

if [ ! -z "${commit}" ]
then
        grep -q '^COMMIT' environment.properties && sed -i "s/^COMMIT.*/COMMIT=${COMMIT}" || echo "COMMIT=${commit}" >> environment.properties
fi

BRANCH=$(grep -q '^BRANCH' environment.properties | sed 's/[^=]*=//')
COMMIT=$(grep -q '^COMMIT' environment.properties | sed 's/[^=]*=//')

if [ x"${COMMIT}" == x ]
then
    echo "GIT_CHECKOUT=${BRANCH}" >> environment.properties
else
    echo "GIT_CHECKOUT=${COMMIT}" >> environment.properties
fi

"""

	helper = new helper();

	handleLists(helper);

	String[] boottests = helper.getEnv("BOOTTESTS_ALL").split();
	String[] configs = helper.getEnv("CONFIGS").split();
	String[] overlays = helper.getEnv("OVERLAYS").split();

	if (boottests) {
		CIRTbuildenvCompileBoot(configs, overlays);
		CIRTbuildenvCyclictest(boottests);
	}
}

private handleLists(helper helper) {
	helper.list2prop("env/compile.list", "CONFIGS", "environment.properties");
	helper.list2prop("env/overlay.list", "OVERLAYS", "environment.properties");
	helper.list2prop("env/boottest.list", "BOOTTESTS_ALL", "environment.properties");
	helper.list2prop("env/email.list", "RECIPIENTS", "environment.properties");

	String[] properties = ["environment.properties"];
	helper.add2environment(properties);
	helper.runShellScript("environment/arch_env_magic.py");
}

def call(String commit, Map global) {
	inputcheck.check(global);
	try {
		node('master') {
			dir('environment') {
				deleteDir();
				unstash(global.STASH_RAWENV);
				buildEnv(commit);
				/*
				 * Stash *.properties files; directory
				 * hierarchy doesn't change
				 */
				stash(includes: '**/*.properties',
				      name: global.STASH_PRODENV);
				stash(includes: 'patches/**',
				      name: global.STASH_PATCHES);
				stash(includes: 'compile/configs/**, compile/overlays/**',
				      name: global.STASH_COMPILECONF);
			}
			archiveArtifacts(artifacts: 'environment/**/*.properties, environment/patches/**, environment/compile/configs/**, environment/compile/overlays/**',
					 fingerprint: true);
		}
	} catch(Exception ex) {
                println("CIRTbuildenv failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("CIRTbuildenv failed.");
        }
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

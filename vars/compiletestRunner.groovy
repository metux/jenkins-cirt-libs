#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import de.linutronix.cirt.inputcheck;
import java.io.File;

private runner(Map global, String repo, String branch,
	       String config, String overlay) {
	println("${repo} ${branch} ${config} ${overlay}");

	def compiledir = "results/${config}/${overlay}";
	println("Repository ${repo} ${branch}");
	println("Compile Job ${config} ${overlay}");

	resultdir = "compile";
	builddir = "build";
	def linuximageprop = "${config}/${overlay}".replaceAll('/','-');
	arch = config.split("/")[0];

	dir(compiledir) {
		deleteDir();

		checkout([$class: 'GitSCM', branches: [[name: "${branch}"]],
			  doGenerateSubmoduleConfigurations: false,
			  extensions: [[$class: 'CloneOption',
					depth: 1, noTags: false,
					reference: '/home/mirror/kernel',
					shallow: true, timeout: 60]],
			  submoduleCfg: [], userRemoteConfigs: [[url: "${repo}"]]]);

		dir(".env") {
			unstash(global.STASH_PRODENV);
			unstash(global.STASH_COMPILECONF);
		}

		/* Unstash and apply test-description patch queue */
		unstash(global.STASH_PATCHES);
		sh("[ -d patches ] && quilt push -a");

		/* Extract gittag information for db entry */
		dir(resultdir) {
			sh """echo "TAGS_COMMIT=\$(git rev-parse HEAD)" >> gittags.properties""";
			sh """echo "TAGS_COMMIT=\$(git describe HEAD)" >> gittags.properties""";
		}

		/* Create builddir and create empty .config */
		/* TODO: better solution for an empty builddir (new File(NAME).mkdir())? */
		dir(builddir) {
			sh '''touch .config''';
		}

		prepconfig = libraryResource('de/linutronix/cirt/compiletest/preparekernel.sh');
		writeFile file:"prepconfig.sh", text:prepconfig;
		sh ". prepconfig.sh ${config} ${overlay} ${resultdir} ${builddir} ${env.BUILD_NUMBER}";

		/*
		 * Use environment file and add an "export " at the
		 * begin of every line, to be includable in compile
		 * bash script; remove empty lines before adding
		 * "export ".
		 */
		def exports = readFile ".env/compile/env/${arch}.properties";
		exports = exports.replaceAll(/(?m)^\s*\n/, "");
		exports = exports.replaceAll(/(?m)^/, "export ");

		def script_content = """#!/bin/bash

# Abort build script if there was an error executing the commands
set -e

# Required environment settings
${exports}
# TODO: The ARCH define will be removed, when ARCH is defined in ARCH.properties file
export ARCH=${arch}

MAKE_PARALLEL=${env.PARALLEL_MAKE_JOBS ?: '16'}
LOCALVERSION=${env.BUILD_NUMBER}
CONFIG_OVERLAY=${linuximageprop}
BUILD_DIR=${builddir}
RESULT_DIR=${resultdir}

# TODO: Replace linuximageprop with linuximage, when property files are with _ instead of -
# TODO: BUILDONLY should depend on BUILD_TARGET; move this logic in environment handling;
#	possible solution: per config environment file, which overwrites arch settings?
BUILDONLY=${fileExists(".env/compile/env/"+linuximageprop+".properties") == "true" ? 1 : 0}
"""+'''

BUILDARGS="-j ${MAKE_PARALLEL}  O=${BUILD_DIR} LOCALVERSION=-${LOCALVERSION}"

# 2 Logfiles: compile log file and package build log file
LOGFILE=${RESULT_DIR}/compile.log
LOGFILE_PKG=${RESULT_DIR}/package.log

# Information for reproducability
if [[ ! -d $BUILD_DIR || ! -d $RESULT_DIR ]]
then
	echo "Directories $BUILD_DIR and $RESULT_DIR are required"
	exit 1
else
	echo "Reminder: Proper kernel config stored in $BUILD_DIR/.config and patches applied?"
fi

echo "compiletest-runner #${LOCALVERSION} $CONFIG_OVERLAY (stderr)" > $LOGFILE
make $BUILDARGS 2> >(tee -a $LOGFILE >&2)

# Make devicetree binaries?
if [ xtrue = x"$MKDTBS" ]
then
	make $BUILDARGS dtbs 2> >(tee -a $LOGFILE >&2)
fi

# If config will be booted later, debian package and devicetrees
# need to be created and stored in $RESULT_DIR
if [ $BUILDONLY -eq 0 ]
then
	echo "compiletest-runner #${LOCALVERSION} $CONFIG_OVERLAY ${BUILD_TARGET:bindeb-pkg} (stderr)" > $LOGFILE_PKG
	make $BUILDARGS ${BUILD_TARGET:-bindeb-pkg} 2> >(tee -a $LOGFILE_PKG >&2)

	if [ -d $BUILD_DIR/arch/${ARCH}/boot/dts ]
	then
		tar cJf $RESULT_DIR/dtbs-${LOCALVERSION}.tar.xz --exclude 'dts/.*' -C $BUILD_DIR/arch/${ARCH}/boot dts
	fi
	cp *deb $RESULT_DIR/
fi
''';

		writeFile file:"${resultdir}/compile-script.sh", text:script_content;
		sh ". ${resultdir}/compile-script.sh";

		def linuximage = "${config}/${overlay}";
		stash(name: linuximage.replaceAll('/','_'),
		      includes: 'compile/linux-image*deb',
		      allowEmpty: true);
	}
	archiveArtifacts(artifacts: "${compiledir}/compile/**",
			 fingerprint: true);

}

def call(Map global, String repo, String branch,
	 String config, String overlay) {
	try {
		inputcheck.check(global);
		dir("compiletestRunner") {
			deleteDir()
			runner(global, repo, branch, config, overlay);
		}
	} catch(Exception ex) {
                println("compiletest runner failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("compiletest runner failed.");
        }
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

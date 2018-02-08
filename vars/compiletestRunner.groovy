#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

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
	def linuximage = "${config}/${overlay}".replaceAll('/','_');
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
			sh """echo "TAGS_NAME=\$(git describe HEAD)" >> gittags.properties""";
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

MAKE_PARALLEL=${env.PARALLEL_MAKE_JOBS ?: '16'}
LOCALVERSION=${env.BUILD_NUMBER}
CONFIG_OVERLAY=${linuximage}
BUILD_DIR=${builddir}
RESULT_DIR=${resultdir}

# TODO: BUILDONLY should depend on BUILD_TARGET; move this logic in environment handling;
#	possible solution: per config environment file, which overwrites arch settings?
BUILDONLY=${fileExists(".env/compile/env/"+linuximage+".properties") == "true" ? 1 : 0}
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
		shunit("compile", "${config}/${overlay}",
		       "bash ${resultdir}/compile-script.sh");
		sh("mv pyjutest.xml ${resultdir}/");
		stash(name: linuximage,
		      includes: "${resultdir}/linux-image*deb, ${resultdir}/dtbs-${env.BUILD_NUMBER}.tar.xz",
		      allowEmpty: true);
	}
	archiveArtifacts(artifacts: "${compiledir}/${resultdir}/**",
			 fingerprint: true);
	stash(name: compiledir.replaceAll('/','_'),
	      includes: "${compiledir}/${resultdir}/pyjutest.xml, " +
			"${compiledir}/${resultdir}/compile-script.sh, " +
			"${compiledir}/${resultdir}/gittags.properties, " +
			"${compiledir}/${resultdir}/package.log, " +
			"${compiledir}/${resultdir}/config, " +
			"${compiledir}/${resultdir}/compile.log, " +
			"${compiledir}/${builddir}/defconfig",
	      allowEmpty: true);
}

private failnotify(Map global, String repo, String branch, String config,
		   String overlay, String recipients)
{
	dir("failurenotification") {
		deleteDir();

		def results = "results/${config}/${overlay}";
		unstash(results.replaceAll('/','_'));

		def gittags = readFile "${results}/compile/gittags.properties";
		gittags = gittags.replaceAll(/(?m)^\s*\n/, "");

		notify("${recipients}",
		       "compiletest-runner - Build # ${env.BUILD_NUMBER} - failed! (total: \${WARNINGS_COUNT})",
		       "compiletestRunner",
		       "${results}/compile/compile.log,${results}/compile/package.log,${results}/compile/config",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay,
			"gittags": gittags]);
	}
}

def call(Map global, String repo, String branch,
	 String config, String overlay, String recipients) {
	try {
		inputcheck.check(global);
		node ('kernel') {
			dir("compiletestRunner") {
				deleteDir()
				runner(global, repo, branch, config, overlay);

				/* Parse compile.log and package.log for warnings */
				def compiledir = "results/${config}/${overlay}";
				warnings(canComputeNew: false,
					 canResolveRelativePaths: false,
					 canRunOnFailed: true,
					 categoriesPattern: '',
					 defaultEncoding: '',
					 excludePattern: '',
					 healthy: '',
					 includePattern: '',
					 messagesPattern: '',
					 parserConfigurations: [[parserName: 'GNU Make + GNU C Compiler (gcc)',
								 pattern: "${compiledir}/**/compile.log"],
								[parserName: 'Linux Kernel Makefile Errors',
								 pattern: "${compiledir}/**/compile.log"],
								[parserName: 'GNU Make + GNU C Compiler (gcc)',
								 pattern: "${compiledir}/**/package.log"],
								[parserName: 'Linux Kernel Makefile Errors',
								 pattern: "${compiledir}/**/package.log"]],
					 unHealthy: '');
			}
		}

		if (currentBuild.result == 'UNSTABLE') {
			failnotify(global, repo, branch, config,
				   overlay, recipients);
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

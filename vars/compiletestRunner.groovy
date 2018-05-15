#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT compiletest runner
 */

import de.linutronix.cirt.inputcheck;
import java.io.File;

class OverlayNotSetException extends RuntimeException {
	OverlayNotSetException (String message) {
		super(message);
	}
}

private prepare_config(Map global, String repo, String branch,
		       String config, String overlay,
		       String resultdir, String builddir) {
	def prepconfig = libraryResource('de/linutronix/cirt/compiletest/preparekernel.sh');
	writeFile file:"prepconfig.sh", text:prepconfig;
	prepconfig = null;

	/*
	 * start with #!/bin/bash to circumvent Jenkins default shell
	 * options "-xe". Otherwise stderr is poluted with confusing
	 * shell trace output and bedevil the user notification.
	 */
	def prepscript = "#!/bin/bash\n. prepconfig.sh ${config} ${overlay} ${resultdir} ${builddir} ${env.BUILD_NUMBER} 2>prepconfig_stderr.log";

	/*
	 * prepconfig.sh returns:
	 * 0 on success
	 * 1 on a usage error
	 * 2 when overlayfile is not available
	 * 3 when CONFIG option is not set properly
	 */
	def ret = sh(script: prepscript, returnStatus: true);
	def prep_stderr = readFile("prepconfig_stderr.log").trim();

	switch(ret) {
	case 0:
		break;
	case 1:
		error("Usage error of prepconfig.sh: " + prep_stderr);
		break;
	case 2:
		/* fall through */
	case 3:
		/* act like junit and mark test as UNSTABLE */
		currentBuild.result = 'UNSTABLE';
		throw new OverlayNotSetException(prep_stderr);
	default:
		error("Unknown abort in prepconfig.sh");
	}
}

private runner(Map global, String repo, String branch,
	       String config, String overlay) {
	println("${repo} ${branch} ${config} ${overlay}");

	def compiledir = "results/${config}/${overlay}";
	println("Repository ${repo} ${branch}");
	println("Compile Job ${config} ${overlay}");

	def resultdir = "compile";
	def builddir = "build";
	def linuximage = "${config}/${overlay}".replaceAll('/','_');
	def arch = config.split("/")[0];

	def result = '';

	try {
		dir(compiledir) {
			deleteDir();
			/*
			 * Specify a depth of 1. If last commit is no tag "git
			 * describe HEAD" will not work. Fetching the whole
			 * history precautionary, takes a lot of time and is
			 * not required when the last commit is a
			 * tag. Fetching the required history is done before
			 * writing the gittags.properties file when required
			 * (see comment below as well).
			 */
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
			sh("if [ -d patches ] ; then quilt push -a ; fi");

			/* Extract gittag information for db entry */
			dir(resultdir) {
				/*
				 * When "git describe HEAD" does not work more
				 * history depth is required; it is possible
				 * as well to "unshallow" the git repository.
				 */
				sh 'git describe HEAD || git fetch --depth 1000';
				sh """echo "TAGS_COMMIT=\$(git rev-parse HEAD)" >> gittags.properties""";
				sh """echo "TAGS_NAME=\$(git describe HEAD)" >> gittags.properties""";
			}

			/*
			 * Create builddir and create empty .config
			 * TODO: better solution for an empty
			 * builddir(new File(NAME).mkdir())?
			 */
			dir(builddir) {
				sh '''touch .config''';
			}

			prepare_config(global, repo, branch, config, overlay,
				       resultdir, builddir);

			/*
			 * Use environment file and add an "export " at the
			 * begin of every line, to be includable in compile
			 * bash script; remove empty lines before adding
			 * "export ".
			 */
			def exports = readFile ".env/compile/env/${arch}.properties";
			exports = exports.replaceAll(/(?m)^\s*\n/, "");
			exports = exports.replaceAll(/(?m)^/, "export ");
			arch = null;

			def script_content = """#!/bin/bash -x

# Abort build script if there was an error executing the commands
set -e

# Required environment settings
${exports}

MAKE_PARALLEL=${env.PARALLEL_MAKE_JOBS ?: '16'}
LOCALVERSION=${env.BUILD_NUMBER}
CONFIG_OVERLAY=${linuximage}
BUILD_DIR=${builddir}
RESULT_DIR=${resultdir}

# TODO: COMPILE_ONLY should depend on BUILD_TARGET; move this logic in environment handling;
#	possible solution: per config environment file, which overwrites arch settings?
# only if *.properties file exists, boottest is executed for kernel config
COMPILE_ONLY=${fileExists(".env/compile/env/"+linuximage+".properties") ? 0 : 1}
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
if [ $COMPILE_ONLY -eq 0 ]
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

			writeFile file:"${resultdir}/compile-script.sh",
				 text:script_content;
			exports = null;
			script_content = null;
			result = shunit("compile", "${config}/${overlay}",
					"bash ${resultdir}/compile-script.sh");
			sh("mv pyjutest.xml ${resultdir}/");
			stash(name: linuximage,
			      excludes: "**/*-dbg_*",
			      includes: "${resultdir}/linux-image*deb, ${resultdir}/dtbs-${env.BUILD_NUMBER}.tar.xz",
			      allowEmpty: true);
			linuximage = null;
		}
	} finally {
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
	return result;
}

private failnotify(Map global, String repo, String branch, String config,
		   String overlay, String recipients, String subject,
		   String attachment, String overlayerrors)
{
	dir("failurenotification") {
		deleteDir();

		def results = "results/${config}/${overlay}";
		unstash(results.replaceAll('/','_'));

		def gittags = readFile "${results}/compile/gittags.properties";
		gittags = gittags.replaceAll(/(?m)^\s*\n/, "");
		if (!overlayerrors?.trim()) {
			overlayerrors = "";
		}

		notify("${recipients}",
		       "${subject}",
		       "compiletestRunner",
		       "${attachment}",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay,
			"gittags": gittags,
			"overlayerrors": overlayerrors]);
	}
}

def call(Map global, String repo, String branch,
	 String config, String overlay, String recipients) {
	try {
		inputcheck.check(global);
		def result = '';
		node ('kernel') {
			dir("compiletestRunner") {
				deleteDir()
				result = runner(global, repo, branch, config, overlay);

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
				compiledir = null;
			}
		}

		if (result == 'UNSTABLE') {
			def results = "results/${config}/${overlay}";

			failnotify(global, repo, branch, config, overlay, recipients,
				   "compiletest-runner failed! (total: \${WARNINGS_COUNT})",
				   "${results}/compile/compile.log,${results}/compile/package.log,${results}/compile/config",
				   null);
		}
		return result;
	} catch(OverlayNotSetException ex) {
		failnotify(global, repo, branch, config, overlay, recipients,
			   "compiletest-runner failed! Overlay not set properly",
			   "", ex.getMessage());

		return 'UNSTABLE';
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

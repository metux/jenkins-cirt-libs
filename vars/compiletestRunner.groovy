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

	dir(compiledir) {
		deleteDir();
		File configFile = new File("${config}");

		CIRThelper {
			extraEnv("config", "${config}");
			extraEnv("overlay", "${overlay}");
			extraEnv("ARCH", configFile.getParent());
			extraEnv("CONFIGNAME", configFile.getName());
			extraEnv("RESULT", "compile");
			extraEnv("BUILD", "build");
			arch = getEnv("ARCH");

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

			unstash(global.STASH_PATCHES);
			sh("[ -d patches ] && quilt push -a");
			runShellScript("compiletest/gittags.sh");

			String[] properties = [".env/environment.properties",
					       ".env/compile/env/${arch}.properties",
					       "compile/gittags.properties"];

			add2environment(properties);
			runShellScript("compiletest/preparekernel.sh");

			/*
			 * Use environment file and add an "export "
			 * at the begin of every line, to be
			 * includable in compile bash script; remove
			 * empty lines before adding "export ".
			 */
			def exports = readFile ".env/compile/env/${arch}.properties";
			exports = exports.replaceAll(/(?m)^\s*\n/, "");
			exports = exports.replaceAll(/(?m)^/, "export ");

			def script_content = """#!/bin/bash

# Exit bash script on error:
set -e

# Required environment settings
${exports}
export ARCH=${arch}

SCHEDULER_ID=${getEnv("SCHEDULER_ID")}
CONFIGNAME=${configFile.getName()}
overlay=${overlay}
BUILD=build
RESULT=compile

"""+'''

# Make devicetree binaries?
if [ x = x"$MKDTBS" ]
then
	export MKDTBSCMD=""
else
	export MKDTBSCMD="make -j ${PARALLEL_MAKE_JOBS:=16} O=build LOCALVERSION=-$SCHEDULER_ID dtbs"
fi


# Build only and do not boot config? packaging of kernel is not required

if [ ! -f .env/compile/env/$ARCH-$CONFIGNAME-$overlay.properties ]
then
	BUILDONLY=1
else
	BUILDONLY=0
fi

# Create build/cmd script to build/package the kernel and dtbs
cat << _EOF_ > $BUILD/cmd
# Abort build script if there was an error executing the commands
set -e

echo "compiletest-runner #$BUILD_NUMBER $ARCH/$CONFIGNAME (stderr)" > $RESULT/compile.log
make -j ${PARALLEL_MAKE_JOBS:=16} O=$BUILD LOCALVERSION=-$SCHEDULER_ID 2> >(tee -a $RESULT/compile.log >&2)
$MKDTBSCMD

_EOF_


# If config will be booted later, debian package and devicetrees
# need to be created and stored in $RESULT
if [ $BUILDONLY -eq 0 ]
then
	cat << _EOF_ >> $BUILD/cmd
make -j ${PARALLEL_MAKE_JOBS:=16} O=$BUILD LOCALVERSION=-$SCHEDULER_ID ${BUILD_TARGET:-bindeb-pkg} 2> >(tee $RESULT/package.log >&2)

if [ -d $BUILD/arch/${ARCH}/boot/dts ]
then
	tar cJf $RESULT/dtbs-${SCHEDULER_ID}.tar.xz --exclude 'dts/.*' -C $BUILD/arch/${ARCH}/boot dts
fi
cp *deb $RESULT/
_EOF_

fi

chmod a+x $BUILD/cmd
cp $BUILD/cmd $RESULT/

# Execute Buildcmd
$BUILD/cmd''';

			writeFile file:"${getEnv("RESULT")}/compile-script.sh", text:script_content;
			sh ". ${getEnv("RESULT")}/compile-script.sh";

			def linuximage = "${config}/${overlay}";
			stash(name: linuximage.replaceAll('/','_'),
			      includes: 'compile/linux-image*deb',
			      allowEmpty: true);
		}

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

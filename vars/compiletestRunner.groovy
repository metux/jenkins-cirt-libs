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
				       ".env/${arch}.properties",
				       "compile/gittags.properties"];

		add2environment(properties);
		runShellScript("compiletest/preparekernel.sh");
		runShellScript("compiletest/compile.sh");

		def linuximage = "${config}/${overlay}";
		stash(name: linuximage.replaceAll('/','_'),
		      includes: 'compile/linux-image*deb',
		      allowEmpty: true);
	}
}

def call(Map global, String repo, String branch,
	 String config, String overlay) {
	try {
		 inputcheck.check(global);
		 runner(global, repo, branch, config, overlay);
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

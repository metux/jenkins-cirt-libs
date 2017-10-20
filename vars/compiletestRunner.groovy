#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

import java.io.File;

def call(Map global, String repo, String branch,
	 String config, String overlay) {
	println("${repo} ${branch} ${config} ${overlay}");

	File configFile = new File("${config}");
	helper = new helper();

	helper.extraEnv("config", "${config}");
	helper.extraEnv("overlay", "${overlay}");
	helper.extraEnv("ARCH", configFile.getParent());
	helper.extraEnv("CONFIGNAME", configFile.getName());
	helper.extraEnv("RESULT", "compile");
	helper.extraEnv("BUILD", "build");
	arch = helper.getEnv("ARCH");

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

	helper.runShellScript("compiletest/gittags.sh");

	String[] properties = [".env/environment.properties",
			       ".env/${arch}.properties",
			       "compile/gittags.properties"];

	helper.add2environment(properties);
	helper.runShellScript("compiletest/preparekernel.sh");
	helper.runShellScript("compiletest/compile.sh");

	def linuximage = "${config}/${overlay}";
	stash(name: linuximage.replaceAll('/','_'),
	      includes: 'compile/linux-image*deb',
	      allowEmpty: true);
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

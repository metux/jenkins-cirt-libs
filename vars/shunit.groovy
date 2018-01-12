#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

import de.linutronix.lib4lib.logger;

def call(String classname, String name, String cmd) {

	Map enspkg = [:]
	enspkg.ensure_pkgs = [[name: 'pyjutest', version: '1.6+stretch251']];
	// TODO skip suite name here and set it in ensureDebPkg
	enspkg.ensure_repo = "deb http://debian.linutronix.de/tools stretch main"
	enspkg.ensure_repo_key = "http://debian.linutronix.de/tools/repo.pub"
	ensureDebPkg(enspkg);

	writeFile(file:"pyjutest.sh", text:cmd);
	sh "chmod a+x ./pyjutest.sh"

	testcmd = "pyjutest";
	if (classname) {
		testcmd += " --class \"${classname}\"";
	}
	if (name) {
		testcmd += " --name \"${name}\"";
	}
	testcmd += " ./pyjutest.sh";
	logger.debugMsg("--- Start Test ---");
	logger.debugMsg(cmd);
	logger.debugMsg("run: " + testcmd);
	sh(testcmd);
	logger.debugMsg("--- End Test ---");
	junit("pyjutest.xml");
}

def call(String classname, String cmd) {
	call(classname, null, cmd);
}

def call(String cmd) {
	call(null, null, cmd);
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

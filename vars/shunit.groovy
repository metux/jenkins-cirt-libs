#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT combine sh Jenkins command with pyjutest
 */

import de.linutronix.lib4lib.logger;

def call(String classname, String name, String cmd) {
	try {
		Map enspkg = [:];
		enspkg.ensure_pkgs = [[name: 'pyjutest', version: '1.6+stretch251']];
		// TODO skip suite name here and set it in ensureDebPkg
		enspkg.ensure_repo = "deb http://debian.linutronix.de/tools stretch main";
		enspkg.ensure_repo_key = "http://debian.linutronix.de/tools/repo.pub";
		ensureDebPkg(enspkg);

		writeFile(file:"pyjutest.sh", text:cmd);
		sh "chmod a+x ./pyjutest.sh";

		/*
		 * ensure stdout is unbuffered (-u option);
		 * see bug: JENKINS-48300
		 */
		def testcmd = "python3 -u /usr/bin/pyjutest";
		if (classname) {
			testcmd += " --class \"${classname}\"";
		}
		if (name) {
			testcmd += " --name \"${name}\"";
		}
		testcmd += " ./pyjutest.sh";
		logger.debugMsg("[${name}] --- Start Test ---");
		logger.debugMsg("[${name}] "+cmd);
		logger.debugMsg("[${name}] run: " + testcmd);
		sh(testcmd);
		logger.debugMsg("[${name}] --- End Test ---");
		return junit_result("pyjutest.xml");
	}  catch(Exception ex) {
		println("shunit failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("shunit failed.");
	}
}

def call(String classname, String cmd) {
	return call(classname, null, cmd);
}

def call(String cmd) {
	return call(null, null, cmd);
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}

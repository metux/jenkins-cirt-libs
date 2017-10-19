/* -*- mode: groovy; -*-
 * CI-RT library test
 */

package de.linutronix.cirt;

def String loadShellScript(String name) {
	script = libraryResource(prefix + name);

	return script;
}

def runShellScript(String name) {
	runShellScript(name, " ");
}

def runShellScript(String name, String args) {
	script = loadShellScript(name);
	println "Running shell script \"${name}\""
	try {
		withEnv(environment.collect { k,v -> return "${k}=${v}" }) {
			sh "${script} ${args}";
		}
	} catch(Exception ex) {
		println("shell script \"${name}\" failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("shell script \"${name}\" failed.");
	}
	println "Finish shell script \"${name}\""
}

def extraEnv(String k, String v) {
	environment[k] = v;
}

def clearEnv() {
	environment = [:];
}

def add2environment(String foldername, String[] names) {
	for (int i = 0; i < names.size(); i++) {
		println "Loading Property ${names.getAt(i)}"
		property = foldername + "/" + names.getAt(i);
		props = readProperties (file: property);

		try {
			props.each {
				k,v -> environment[k] = v;
			};
		}
		catch (Exception e) {
			println e.toString();
			error "Fail to add entry to environment."
		}
	};
}

def add2environment(String[] names) {
	add2environment(".", names);
}

def showEnv() {
	environment.each {println it}
}

def getEnv() {
	return environment;
}

def getEnv(String name) {
	return environment[name];
}

def list2prop(String listfile, String name, String propertyfile) {
	String listtext = readFile(listfile);

	entry = listtext.replaceAll("#.*\n", " ").replaceAll("\n", " ");
	val = "${name} = ${entry}";

	sh("echo \"${val}\" >> ${propertyfile}");
}

class CIRThelperWithEnv extends CIRThelper {
	Map environment = [:];
	String prefix = "de/linutronix/cirt/";
}

def call(body) {
	body.delegate = new CIRThelperWithEnv();
	body();
}

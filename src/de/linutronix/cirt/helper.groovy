#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT environment helper
 */

package de.linutronix.cirt;

import groovy.transform.Field

@Field String prefix = "de/linutronix/cirt/";
@Field Map environment = [:];

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

/*
 * getEnv() usage in CI-RT (please do not remove):
 *
 * The function getEnv(String name) was previously used to get environment
 * variables in a common fashion. This common behaviour i.e. return null
 * on nonexisting variables may let to silently ignored tests.
 * getEnv() is replaced by getVar() in CI-RT.
 * getVar() came in two fashions without and with preset:
 * - getVar(String name) should be used if an environment is mandatory in any
 *   kind, by the testdescription requirements or any computed must set
 *   environment variable.
 * - getVar(String name, String preset) should be used for all other
 *   environment variables. preset should be set to a reasonable value meant
 *   as documentation also.
 */

def getVar(String name) {
	def e = environment[name];

	if (!e) {
		throw new VarNotSetException(name);
	}

	return e;
}

def getVar(String name, String preset) {
	def e = environment[name];

	if (!e) {
		return preset;
	} else {
		return e;
	}
}

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

def getEnv(String name) {
	return environment[name];
}

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

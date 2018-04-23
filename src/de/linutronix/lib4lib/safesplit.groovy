#!/usr/bin/env groovy
/*
 * CI-RT Jenkins serialization safe split function
 */

package de.linutronix.lib4lib;

/**
 * split - serialization safe split function
 * @param s	string to split into a string array
 */
@NonCPS
static String[] split(String s) {
	return s.split();
}

/**
 * split - serialization safe split function
 * @param s	string to split into a string array
 * @param re	delimiter regular expression
 */
@NonCPS
static String[] split(String s, String re) {
	return s.split(re);
}

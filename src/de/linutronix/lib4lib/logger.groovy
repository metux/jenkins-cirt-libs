#!/usr/bin/env groovy
/*
 * CI-RT Jenkins logger extension
 */

package de.linutronix.lib4lib;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * debugMsg - Log a message to Jenkins Log (Level.FINE)
 * @param msg	log message
 */
static debugMsg(String msg) {
	Logger.getLogger("lib4lib").log(Level.FINE, msg);
}

/**
 * infoMsg - Log a message to Jenkins Log (Level.INFO)
 * @param msg	log message
 */
static infoMsg(String msg) {
	Logger.getLogger("lib4lib").log(Level.INFO, msg);
}

/**
 * warnMsg - Log a message to Jenkins Log (Level.WARNING)
 * @param msg	log message
 */
static warnMsg(String msg) {
	Logger.getLogger("lib4lib").log(Level.WARNING, msg);
}

/**
 * errorMsg - Log a message to Jenkins Log (Level.SEVERE)
 * @param msg	log message
 */
static errorMsg(String msg) {
	Logger.getLogger("lib4lib").log(Level.SEVERE, msg);
}

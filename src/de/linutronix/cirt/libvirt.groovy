#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

/* get libvirt hypervisor URI
 * 1) iterate through slaves to find the target.
 * 2) verify that target is controlled by libvirt
 * 3) get hypervisor URI and return map to set HYPERVISOR in environment
 * throw an AbortException on error
 */
import jenkins.model.*
import hudson.model.*
import hudson.util.*
import hudson.node_monitors.*
import hudson.slaves.*

static String getURI(String target) {
	def jenkins = Jenkins.instance;

        for (slave in jenkins.slaves) {
                def computer = slave.computer
                if (computer.name == target) {
			def launcher = slave.getLauncher();
			if (!launcher.toString().contains('VirtualMachineLauncher'))
				throw new hudson.AbortException("Slave ${target} is not controled by libvirt. Abort.");
				def hyper = launcher.findOurHypervisorInstance();
				return hyper.getHypervisorURI();
		}
	}

	throw new hudson.AbortException("Target ${target} is not available. Abort.");
}

static wait4onlineTimeout(String target, Integer timeout) {
	def jenkins = Jenkins.instance

	for (slave in jenkins.slaves) {
		def computer = slave.computer
		if (computer.name == target) {
			def i;
			for (i = 0; i < timeout; i++) {
				if (computer.isOffline()) {
					/*
					 * Groovy sleep implementation is used in this context;
					 * time is specified in milliseconds
					 */
					sleep(1000);
				}
			}

			if (computer.isOffline()) {
				throw new hudson.AbortException("Target ${computer.name} is offline. Abort: ${computer.getOfflineCauseReason()}")
			}

			println "Target ${computer.name} is online"
		}
	}
}

static offline(String target, String reason, Boolean fail = true) {
	def jenkins = Jenkins.instance

	for (slave in jenkins.slaves) {
		def computer = slave.computer
		if (computer.name == target) {
			if (computer.isOffline()) {
				if (fail) {
					    throw new hudson.AbortException("Target ${computer.name} is offline. Abort.");
				} else {
					println("Target ${computer.name} is already offline.");
				}
			}
			println("Offline ${computer.name}: ${reason}");
			computer.doToggleOffline("${reason}")
		}
	}
}

static online(String target, String reason, Boolean fail = true) {
	def jenkins = Jenkins.instance

	for (slave in jenkins.slaves) {
		def computer = slave.computer
		if (computer.name == target) {
			if (computer.isOnline()) {
				if (fail) {
					throw new hudson.AbortException("Target ${computer.name} is online. Abort.");
				} else {
					println("Target ${computer.name} is already online.");
					return;
				}
			}
			println("Online ${computer.name}: ${reason}");
			computer.doToggleOffline("${reason}")
		}
	}
}

#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library kernel build test
 */

package de.linutronix.cirt;

private prepareEnv(String config, String overlay) {
	helper = new helper();
	helper.extraEnv("config", config);
	helper.extraEnv("overlay", overlay);
        helper.runShellScript("environment/compile-boot.sh");
}

private buildCompBootEnv(String[] configs, String[] overlays) {
	for (int i = 0; i < configs.size(); i++) {
		for (int j = 0; j < overlays.size(); j++) {
			prepareEnv(configs.getAt(i),
				   overlays.getAt(j));
		}
	}
}

def call(String[] configs, String[] overlays) {
	buildCompBootEnv(configs, overlays);
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}

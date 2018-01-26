#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * CI-RT library cyclictest handling
 */

import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.helper;

def call(Map global, String target, String[] cyclictests) {
	try {
                inputcheck.check(global);
		node('master') {
			dir ("cyclictest") {
				deleteDir();
				for (int i = 0; i < cyclictests.size(); i++) {
					ct = cyclictests[i];

					/*
					 * Cyclictest runner is executed on target;
					 * workspace directory doesn't has to be changed
					 */
					cyclictestRunner(global, target, ct);

					unstash(global.STASH_PRODENV);
					h = new helper();
					String[] properties = ["environment.properties",
						"boot/${target}.properties",
						"${ct}.properties"];
					h.add2environment(properties);
					config = h.getEnv("CONFIG");
					overlay = h.getEnv("OVERLAY");
					kernel = "${config}/${overlay}";

					cyclictestdir = "results/${kernel}/${target}/${ct}";
					unstash(ct.replaceAll('/','_'));

					script_content = libraryResource('de/linutronix/cirt/cyclictest/cyclictest2xml.py');
					writeFile file:"collect", text:script_content;
					sh("python3 collect ${ct} ${cyclictestdir}\
						--entryowner ${h.getEnv('ENTRYOWNER')}\
						--duration ${h.getEnv('DURATION')}\
						--interval ${h.getEnv('INTERVAL')}\
						--limit ${h.getEnv('LIMIT')}")

					junit("${cyclictestdir}/pyjutest.xml");
					archiveArtifacts("${cyclictestdir}/pyjutest.xml");
					stash(name: ct.replaceAll('/','_'),
					      includes: "${cyclictestdir}/histogram.*," + \
							"${cyclictestdir}/pyjutest.xml");
				}
			}
		}
	} catch(Exception ex) {
                println("cyclictest on ${target} failed:");
                println(ex.toString());
                println(ex.getMessage());
                println(ex.getStackTrace());
                error("cyclictest on ${target} failed.");
        }
}

def call(String... params) {
        println params
        error("Unknown signature. Abort.");
}

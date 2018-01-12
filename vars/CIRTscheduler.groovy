/* -*- mode: groovy; -*-
 * CI-RT cyclictest runner job
 */

import de.linutronix.cirt.inputcheck;

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def global = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = global
	body()

	pipeline {
		stage('inputcheck') {
			inputcheck.check(GUI_TESTDESCR_BRANCH, global);
			println("global variables set.");
		}

		stage('checkout-testdescription') {
			node('master') {
				dir('rawenv') {
					deleteDir();
					git(branch: GUI_TESTDESCR_BRANCH,
					    changelog: false, poll: false,
					    url: global.TESTDESCRIPTION_REPO);
					stash(global.STASH_RAWENV);
				}
			}
		}

		stage('build CI-RT environment') {
			CIRTbuildenv(GUI_COMMIT, global);
		}

		stage('compile') {
			node('kernel') {
				compiletest(global);
			}
		}

		stage('collect and inform') {
			node('master') {
				println("feed database");
				println("send email");
			}
		}
	}
}

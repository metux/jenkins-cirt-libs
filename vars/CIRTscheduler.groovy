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
		agent any;

		options {
			timestamps()
		}

		parameters {
			string(defaultValue: "${global.STASH_PATCHES ?: 'patches'}", description: '', name: 'STASH_PATCHES')
			string(defaultValue: "${global.STASH_PRODENV ?: 'prodenv'}", description: '', name: 'STASH_PRODENV')
			string(defaultValue: "${global.STASH_RAWENV ?: 'rawenvironment'}", description: '', name: 'STASH_RAWENV')
			string(defaultValue: "${global.STASH_COMPILECONF ?: 'compileconf'}", description: '', name: 'STASH_COMPILECONF')
			string(defaultValue: "${global.GUI_DB_HOSTNAME ?: 'localhost?5432'}", description: 'Hostname of database', name: 'GUI_DB_HOSTNAME')
			string(defaultValue: "${global.GUI_COMMIT ?: ''}", description: '', name: 'GUI_COMMIT')
		}

		stages {
			stage('inputcheck') {
				steps {
					CIRTinputcheck(params)
					echo "global variables set."
				}
			}

			stage('stash test-description') {
				steps {
					stash("${params.STASH_RAWENV}");
				}
			}

			stage('build CI-RT environment') {
				steps {
					script {
						try {
							CIRTbuildenv(params.GUI_COMMIT, params);
						} catch(Exception ex) {
							println("build environment failed:");
							println(ex.toString());
							println(ex.getMessage());
							println(ex.getStackTrace());
							error("build environment failed.");
						}
					}
				}
			}

			stage('compile') {
				steps {
					script {
						try {
							compiletest(params);
						} catch(Exception ex) {
							println("compiletest failed:");
							println(ex.toString());
							println(ex.getMessage());
							println(ex.getStackTrace());
							error("compiletest failed.");
						}
					}
				}
			}

			stage('collect and inform') {
				environment {
					DB_HOSTNAME = "${params.GUI_DB_HOSTNAME}";
				}
				steps {
					echo "feed database"
					withCredentials([[$class: 'UsernamePasswordMultiBinding',
							  credentialsId: 'POSTGRES_CREDENTIALS',
							  usernameVariable: 'DB_USER',
							  passwordVariable: 'DB_PASS']]) {
						script {
							try {
								feeddatabase(params);
							} catch(Exception ex) {
								println("feeddatabase failed:");
								println(ex.toString());
								println(ex.getMessage());
								println(ex.getStackTrace());
								error("feeddatabase failed.");
							}
						}
					}
					echo "send email"
				}
			}
		}
	}
}

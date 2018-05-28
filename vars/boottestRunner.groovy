#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT boottest runner
 */

import de.linutronix.cirt.TargetOnOfflineException;
import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;
import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.libvirt;
import de.linutronix.lib4lib.safesplit;

import hudson.AbortException

class BoottestException extends RuntimeException {
        BoottestException (String message) {
                super(message);
        }
}

class RebootException extends RuntimeException {
        RebootException (String message) {
                super(message);
        }
}

class ForcedRebootException extends RuntimeException {
        ForcedRebootException (String message) {
                super(message);
        }
}

class ForcedRebootAndBoottestException extends RuntimeException {
        ForcedRebootAndBoottestException (String message) {
                super(message);
        }
}

private rebootTarget(String hypervisor, String target, String seriallog,
		     Boolean testboot, Boolean forced, Boolean regrtest) {
	if (testboot && forced) {
		error ("set testboot and forced in rebootTarget() is invalid");
	}

	def pidfile = "seriallogpid";
	def off_message = "";

	if (testboot) {
		off_message = "Reboot to Kernel build (${env.BUILD_TAG})";;
	} else {
		off_message = "Reboot to default Kernel";
	}

	/*
	 * TODO: Ignore serial boot log on default boot for now.
	 * QEMU drops the connection to the serial console on destroy.
	 * virsh console stopps therefore. Later on the test trys to
	 * kill a non-existing process which results in a test abort.
	 */

	if (testboot) {
		def virshoutput = "virshoutput";

		/* Start serial console logging on test boot */
		/* TODO: Cleanup error handling; it is not the best solution */
		writeFile file: virshoutput, text: '';
		sh """virsh -c ${hypervisor} consolelog ${target} --force --logfile ${seriallog} 2> ${virshoutput} || echo \"virsh error!\" >> ${virshoutput}"""+'''&
echo $! >'''+""" ${pidfile}"""

		/*
		 * Wait 5 seconds to be sure that virsh workes properly and
		 * error file was written.
		 */
		sleep(time: 5, unit: 'SECONDS');
		def output = readFile(virshoutput).trim()
		virshoutput = null;
		if (output) {
			println("Virsh console logging Problem (target properly written and logfile writeable?): " + output);
			/* TODO: throw exception; IT Problem */
		}
	}

	/*
	 * Set taget offline.
	 * Fail on an offline node on a test boot, warn otherwise.
	 */
	libvirt.offline(target, off_message, testboot);
	off_message = null;

	/*
	 * Brave New World: systemd kills the network before ssh
	 * terminates, therefore -t +1, witch is really now + a bit;
	 * force reboot only when booting into default kernel.
	 */
	println("Reboot Target ${target}");

	/*
	 * Do nothing to forced restart the target when
	 * "REGRESSION_TEST_FORCED_REBOOT" is set in test description,
	 * it is for regression test purpose only
	 */
	if (!forced) {
		/*
		 * Not forced reboot for testboot and after
		 * successfull testboot
		 */
		sh "ssh ${target} \"sudo shutdown -r -t +1\"";
	} else if (!regrtest) {
		/*
		 * Reboot into default kernel: If board didn't came up
		 * properly, the reboot is forced. Sleep between
		 * destroy and start for half a minute to be sure
		 * capacitors do not create wrong state on poweron.
		 */
		sh """(virsh -c ${hypervisor} destroy ${target}; sleep 30; virsh -c ${hypervisor} start ${target})""";
	}

	/*
	 * Sleep more than 5 Minutes - it is the Jenkins slave ping
	 * timeout. Please look at:
	 * https://wiki.jenkins.io/display/JENKINS/Ping+Thread.
	 * Furthermore, all targets should be online afterwards.
	 */
	sleep(time: 330, unit: 'SECONDS');

	/*
	 * TODO: Ignore serial boot log on default boot for now.
	 * See above.
	 */
	if (testboot) {
		println("kill seriallog");
		def pid = readFile(pidfile).trim();
		pidfile = null;
		sh "kill ${pid}";
	}
}

private rebootTarget(String hypervisor, String target, String seriallog,
		     Boolean testboot, Boolean forced) {
	rebootTarget(hypervisor, target, seriallog, testboot, forced, false);
}

private extractSerialBootlog(String seriallog, String bootlog) {
	def kexec_delimiter = "--- CI-RT Booting Testkernel Kexec ---";

	/* ensure seriallog is synced before reading */
	sh "sync";

	/* extract kexec bootlog of serial log*/
	def serial_content = readFile(seriallog);
	def serial_splits = safesplit.split(serial_content, kexec_delimiter);

	/* error, if kexec_delimiter do not occur, or occurs more than one time */
	def cnt = serial_splits.size() - 1;
	if (cnt != 1) {
		writeFile file:bootlog, text:serial_content;
		error message:"kexec delimiter \"${kexec_delimiter}\" occurs "+cnt+"time";
	} else {
		/* remove all lines which are no kernel output */
		def boot_content = serial_splits[1].replaceAll(/(?m)^[^\[]*/, "");
		writeFile file:bootlog, text:boot_content;
	}
}

private checkOnline(String target, Boolean testboot, Boolean forced) {
	if (testboot && forced) {
		error ("set testboot and forced in checkOnline() is invalid");
	}

	def versionfile = "kversion";
	def on_message = "";

	if (testboot) {
		on_message = "In test kernel of Kernel build (${env.BUILD_TAG})";
	} else {
		on_message = "In default Kernel";
	}

	try {
		/* Test the ssh connection if the target is back online */
		sh "ssh -o ConnectTimeout=10 -o ConnectionAttempts=6 ${target} uname -r";

		/* Set target online */
		libvirt.online(target, on_message, testboot);
		on_message = null;
	} catch (AbortException | TargetOnOfflineException ex) {
		println("Target ${target} is not online after 310 seconds");

		def msg = "Target ${target} is not online after 310 seconds";
		if (testboot) {
			throw new BoottestException(msg);
		} else if (!forced) {
			throw new RebootException(msg);
		} else {
			throw new ForcedRebootException(msg);
		}
	}

	/* Test for the proper kernel version */
	sh 'echo $(ssh ' + target +
		' uname -r | sed \"s/.*-rt[0-9]\\+\\(-rc[0-9]\\+\\)\\?-\\([0-9]\\+\\).*$/\\2/\") > ' +
		versionfile;
	def version = readFile(versionfile).trim();
	versionfile = null;

	if (testboot && version != env.BUILD_NUMBER) {
		println("The booted kernel version \"${version}\" on target ${target} differs from version under test.");

		error message:"Boottest failed! IT Problem!";
	}
	/* TODO add a check for the default Kernel */

	println("Target is back");
}

private forcedRebootDefault(String hypervisor, String target, String seriallog,
			    Boolean bootexception, helper helper) {
	println("Forced reboot into default kernel");
	try {
		Boolean regrtest = helper.getVar("REGRESSION_TEST_FORCED_REBOOT", "false").toBoolean();

		rebootTarget(hypervisor, target, seriallog, false, true, regrtest);
		checkOnline(target, false, true);
	} catch (ForcedRebootException ex) {
		if (bootexception) {
			throw new ForcedRebootAndBoottestException(ex.getMessage());
		} else {
			throw ex;
		}
	}
}

private runner(Map global, helper helper, String boottest, String boottestdir, String resultdir, String recipients) {
	def target = helper.getVar("TARGET");

	def hypervisor = libvirt.getURI(target);
	println("URI = ${hypervisor}");

	def config = helper.getVar("CONFIG");
	def overlay = helper.getVar("OVERLAY");

	/* remember: config and overlay may contain slashes */
	def kernelstash = "${config}_${overlay}".replaceAll('/','_');

	dir(boottestdir) {
		deleteDir();
		lock(target) {
			def cmdlinefile = "${resultdir}/cmdline";
			def seriallog_default = "${resultdir}/serialboot-default.log";
			def seriallog = "${resultdir}/serialboot.log";
			def bootlog = "${resultdir}/boot.log";

			/*
			 * Create result directory and add cmdlinefile
			 * and bootlog file with default content. This
			 * prevents that a test system exception
			 * (caused by a test system error) is
			 * overwritten by a missing file exception in
			 * the finally section.
			 */
			dir(resultdir) {
				writeFile file:"${cmdlinefile}" - "${resultdir}/", text:'none';
				writeFile file:"${bootlog}" - "${resultdir}/", text:'--- no boot log available ---';
				writeFile file:"${seriallog}" - "${resultdir}/", text:'--- no boot log available ---';
			}

			libvirt.wait4onlineTimeout(target, 120);

			targetprep(global, target, kernelstash);
			kernelstash = null;

			try {
				rebootTarget(hypervisor, target, seriallog, true, false);

				extractSerialBootlog(seriallog, bootlog);
				seriallog = null;
				bootlog = null;

				checkOnline(target, true, false);

				/* write cmdline to file */
				sh "echo \$(ssh ${target} cat /proc/cmdline) > ${cmdlinefile}";
				cmdlinefile = null;

				def cyclictests = safesplit.split(helper.getVar("CYCLICTESTS", " "));
				if (cyclictests) {
					cyclictest(global, target, cyclictests,
						   recipients);
				}

				println("Not forced reboot into default kernel");
				rebootTarget(hypervisor, target, seriallog_default, false, false);
				checkOnline(target, false, false);
			} catch (BoottestException ex) {
				forcedRebootDefault(hypervisor, target, seriallog_default, true, helper);
				throw ex;
			} catch (RebootException ex) {
				def repo = helper.getVar("GITREPO");
				def branch = helper.getVar("BRANCH");
				failnotify(global,
					   "boottestRunner - Reboot of \"${target}\" failed",
					   "boottestReboot", repo, branch,
					   config, overlay, resultdir,
					   recipients, target);
				/* act like junit and mark test as UNSTABLE but do not fail */
				currentBuild.result = 'UNSTABLE';
				forcedRebootDefault(hypervisor, target, seriallog_default, false, helper);
			}
		}
	}
}

private failnotify(Map global, String subject, String template, String repo,
		   String branch, String config, String overlay,
		   String resultdir, String recipients, String target,
		   Map extras)
{
	def results = "results/${config}/${overlay}";

	dir("failurenotification") {
		deleteDir();
		unstash(results.replaceAll('/','_'));

		/*
		 * Specifying a relative path starting with "../" does not
		 * work in notify attachments.
		 * boot.log only exists for booting kernel under test
		 * i.e. not for forced boot into default kernel.
		 * Copy boot.log, if exists, into this folder.
		 */
		def forcedboot = extras['forcedboot'];
		if (!forcedboot) {
			sh("cp ../${resultdir}/boot.log .");
		}

		def gittags = readFile "${results}/compile/gittags.properties";
		gittags = gittags.replaceAll(/(?m)^\s*\n/, "");

		notify("${recipients}",
		       "${subject}",
		       "${template}",
		       "boot.log,${results}/compile/config",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay, "target": target,
			"gittags": gittags] + extras);
	}
}

private failnotify(Map global, String subject, String template, String repo,
		   String branch, String config, String overlay,
		   String resultdir, String recipients, String target)
{
	failnotify(global, subject, template, repo, branch, config, overlay,
		   resultdir, recipients, target, [:]);
}

def call(Map global, String boottest, String recipients) {
	def failed = false;
	def boottestdir = "";
	def resultdir = "boottest";
	def repo = "";
	def branch = "";
	def config = "";
	def overlay = "";
	def target = "";
	def h = new helper();
	try {
		inputcheck.check(global);
		dir("boottestRunner") {
			deleteDir();

			unstash(global.STASH_PRODENV);
			String[] properties = ["environment.properties",
					       "${boottest}.properties"];
			h.add2environment(properties);

			target = h.getVar("TARGET");
			repo = h.getVar("GITREPO");
			branch = h.getVar("BRANCH");
			config = h.getVar("CONFIG");
			overlay = h.getVar("OVERLAY");
			def kernel = "${config}/${overlay}";

			/* Last subdirectory "boottest" for results is created by scripts */
			boottestdir = "results/${kernel}/${target}";
			kernel = null;

			runner(global, h, boottest, boottestdir, resultdir,
			      recipients);
			h = null;
		}
	} catch (ForcedRebootAndBoottestException ex) {
		failed = true;
		if (global.GUI_FAILURE_NOTIFICATION) {
			failnotify(global,
				   "Forced reboot of \"${target}\" failed",
				   "boottestRunner", repo, branch, config,
				   overlay, resultdir,
				   "${global.GUI_FAILURE_NOTIFICATION}",
				   target, ["forcedboot": true,
					    "bootexception": true]);
		}
	} catch (ForcedRebootException ex) {
		/* do not fail on dut testkernel reboot failures */
		failed = false;
		if (global.GUI_FAILURE_NOTIFICATION) {
			failnotify(global,
				   "Forced reboot of \"${target}\" failed",
				   "boottestRunner", repo, branch, config,
				   overlay, resultdir,
				   "${global.GUI_FAILURE_NOTIFICATION}",
				   target, ["forcedboot": true,
					   "bootexception": false]);
		}
	} catch (BoottestException ex) {
		failed = true;
	} catch (TargetOnOfflineException ex) {
		println("On/Offline problem with target: ${target}");

		resultdir = "boottestRunner/${boottestdir}/" + resultdir;
		/* inform both: test user and test system admins */
		failnotify(global,
			   "On/Offline problem with target: ${target}",
			   "offlineTarget", repo, branch, config,
			   overlay, resultdir,
			   "${global.GUI_FAILURE_NOTIFICATION} ${recipients}",
			   target);
		/* act like junit and mark test as UNSTABLE */
		currentBuild.result = 'UNSTABLE';
		return;
	} catch(Exception ex) {
		if (ex instanceof VarNotSetException) {
                        throw ex;
                }
		println("boottest \"${boottest}\" failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("boottest \"${boottest}\" failed.");
	}

	dir("boottestRunner") {
		try {
			resultdir = "${boottestdir}/" + resultdir;

			/* parse boot.log for warnings */
			warnings(canComputeNew: false,
				 canResolveRelativePaths: false,
				 canRunOnFailed: true,
				 categoriesPattern: '',
				 defaultEncoding: '',
				 excludePattern: '',
				 healthy: '',
				 includePattern: '',
				 messagesPattern: '',
				 parserConfigurations: [[parserName: 'Linux Kernel Output Parser',
							 pattern: "${resultdir}/boot.log"]],
				 unHealthy: '');

			def script_content = libraryResource('de/linutronix/cirt/boottest/boottest2xml.py');
			writeFile file:"boottest2xml", text:script_content;
			script_content = null;

			/*
			 * Do not stash boot log on boot failures:
			 * boot.log is not filtered and flood the
			 * warnings plugin with new bootlog entries for
			 * no value. Contrary information may got loss
			 * in the chatter.
			 */
			def attachments = "${resultdir}/pyjutest.xml, " +
				"${resultdir}/cmdline";

			if (failed) {
				sh("python3 boottest2xml ${boottest} ${boottestdir} failure")
			} else {
				sh("python3 boottest2xml ${boottest} ${boottestdir}")
				attachments = "${attachments}, ${resultdir}/boot.log";
			}

			archiveArtifacts(artifacts: "${resultdir}/**",
					 fingerprint: true);
			stash(name: boottest.replaceAll('/','_'),
			      includes: attachments);

			junit("${resultdir}/pyjutest.xml");

			if (!failed) {
				return;
			}

			failnotify(global,
				   "boottest-runner failed! (total: \${WARNINGS_COUNT})",
				   "boottestRunner", repo, branch, config,
				   overlay, resultdir, recipients, target);
		} catch(Exception ex) {
			println("boottest \"${boottest}\" postprocessing failed:");
			println(ex.toString());
			println(ex.getMessage());
			println(ex.getStackTrace());
			error("boottest \"${boottest}\" postprocessing failed.");
		}
	}
}

def call(String... params) {
	println params
        error("Unknown signature. Abort.");
}

#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * lib4lib - ensure a Debian packages are installed
 */

import de.linutronix.lib4lib.logger;

def check_pkgversion(String pkg, String dver)
{
	// raises an exception if pkg is not available
	sh("dpkg -l $pkg | grep ^ii");
	if (dver != "None") {
		// used to check version
		def dpkg = sh(returnStdout: true, script: "dpkg -l $pkg | grep ^ii");
		def dpkg_pkg = dpkg.split()[1];
		def dpkg_ver = dpkg.split()[2];
		// this raises an exception if the package version is not correct
		sh("dpkg --compare-versions $dpkg_ver = $dver");
	}
}

def call(Map p = [:])
{
	if (!p) {
		logger.errorMsg("ensureDebPkg is called with invalid parameters.");
		error("ensureDebPkg failed: invalid parameters");
	}

	if (!p.ensure_pkgs) {
		logger.errorMsg("ensureDebPkg is called with empty package list.");
		error("ensureDebPkg failed: empty package list");
	}

	for (int i = 0; i < p.ensure_pkgs.size; i++) {
		def pkg = p.ensure_pkgs[i].name;
		def dver = "None";
		if (p.ensure_pkgs[i].version)
			dver = p.ensure_pkgs[i].version;
		try {
			check_pkgversion(pkg, dver);
		} catch (err) {
			// ensure reinstall and dont fail if not installed
			sh("sudo apt-get --yes purge $pkg | /bin/true");
			// TODO guess suite name if not specified
			sh("echo \'$p.ensure_repo\' | sudo tee /etc/apt/sources.list.d/ensure.list");
			if (dver != "None") {
				sh("echo \'Package: $pkg\nPin: version $dver\nPin-Priority: 1001\' | sudo tee /etc/apt/preferences.d/ensure.pref");
			}
			sh("wget -O - $p.ensure_repo_key | sudo apt-key add -");
			sh("sudo apt-get update");
			sh("sudo apt-get install --yes $pkg");
			sh("sudo rm -f /etc/apt/sources.list.d/ensure.list /etc/apt/preferences.d/ensure.pref");
		}
		try {
			check_pkgversion(pkg, dver);
		} catch (err) {
			logger.errorMsg("cannot install package: $pkg in desired version: $dver");
			throw (err);
		}
	}
}

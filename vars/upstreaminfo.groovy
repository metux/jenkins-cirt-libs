#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * get upstream project information
 */

import de.linutronix.lib4lib.upstream;

def call()
{
	return ["upstreamBuild": upstream.build(currentBuild),
		"upstreamProject": upstream.project(currentBuild),
		"upstreamURL": Hudson.instance.getRootUrl() +
		upstream.url(currentBuild)];
}

#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * get upstream project information
 */

package de.linutronix.lib4lib;

static String build(current)
{
	return current.rawBuild.getCause(hudson.model.Cause$UpstreamCause)?.getUpstreamBuild() ?:
	"No upstream build - current ${current.number}";
}

static String project(current)
{
	return current.rawBuild.getCause(hudson.model.Cause$UpstreamCause)?.getUpstreamProject() ?:
	"No upstream build";
}

static String url(current)
{
	return current.rawBuild.getCause(hudson.model.Cause$UpstreamCause)?.getUpstreamUrl() ?:
	"No upstream build - ${current.absoluteUrl}";
}

#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT notification helper
 */

import groovy.text.StreamingTemplateEngine
import java.net.URI

@NonCPS
def renderTemplate(String input, Map variables)
{
	/* remove JSP-style comments */
	input = input.replaceAll(/(?s)<%--.*--%>/, "");

	def engine = new StreamingTemplateEngine();
	return engine.createTemplate(input).make(variables).toString();
}

@NonCPS
def prepareSubject(String subject)
{
	subject = "${env.BRANCH_NAME} #${env.BUILD_NUMBER} - ${subject}";
	URI uri = new URI(Hudson.instance.getRootUrl());
	return "[${uri.getHost().replaceAll(/\..*/, "")}] ${subject}";
}

def call(String to, String subject, String template, Map variables)
{
	call(to, subject, template, null, true, variables);
}

def call(String to, String subject, String template, Boolean log)
{
	call(to, subject, template, null, log, [:]);
}

def call(String to, String subject)
{
	call(to, subject, "default", null, true, [:]);
}

def call(String to, String subject, String templatename, String attachment,
	 Boolean log, Map variables)
{
	if (to == null) {
		error("No E-Mail adress given. Abort.");
	}

	if (templatename == null) {
		error("No E-Mail template given. Abort.");
	}

	if (attachment) {
		stash(includes: attachment, allowEmpty: true,
		      name: 'attachment');
	}

	node('master') {
		dir('notify') {
			deleteDir();
			if (attachment) {
				unstash('attachment');
			}

			println("Send Email notification to ${to}");
			println("Email template: ${templatename}");

			variables << env.getEnvironment();
			variables['GIT_URL'] = env.GIT_URL;
			variables['GIT_COMMIT'] = env.GIT_COMMIT;

			def template = libraryResource("de/linutronix/cirt/email/${templatename}.txt");
			def body = renderTemplate(template, variables);
			subject = prepareSubject(subject);

			emailext(subject: subject, body: body, attachLog: log,
				 attachmentsPattern: attachment,
				 mimeType: "text/plain", to: to,
				 compressLog: true);
		}
	}
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}

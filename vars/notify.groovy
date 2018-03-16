#!/usr/bin/groovy
/* -*- mode: groovy; -*-
 * notification helper
 */

import groovy.text.StreamingTemplateEngine
import java.net.URI

def renderTemplate(String input, Map variables)
{
	def engine = new StreamingTemplateEngine();
	return engine.createTemplate(input).make(variables).toString();
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

	println("Send Email notification to ${to}");
	println("Email template: ${templatename}");

	variables << currentBuild.getRawBuild().getEnvironment();
	variables['GIT_URL'] = env.GIT_URL;
	variables['GIT_COMMIT'] = env.GIT_COMMIT;

	subject = "${env.BRANCH_NAME} #${env.BUILD_NUMBER} - ${subject}";

	def template = libraryResource("de/linutronix/cirt/email/${templatename}.txt");
	/* remove JSP-style comments */
	template = template.replaceAll(/(?s)<%--.*--%>/, "");
	def body = renderTemplate(template, variables);

	URI uri = new URI(Hudson.instance.getRootUrl());
	subject = "[${uri.getHost().replaceAll(/\..*/, "")}] ${subject}";

	emailext(subject: subject, body: body, attachLog: log,
		 attachmentsPattern: attachment, mimeType: "text/plain",
		 to: to);
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}

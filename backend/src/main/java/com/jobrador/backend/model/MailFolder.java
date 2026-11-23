package com.jobrador.backend.model;

/** Outlook folders we scan, mapped to Microsoft Graph well-known folder names. */
public enum MailFolder {
	INBOX("inbox"),
	JUNK("junkemail");

	private final String graphName;

	MailFolder(String graphName) {
		this.graphName = graphName;
	}

	public String graphName() {
		return graphName;
	}
}

package com.note.application.security;

public class AuthenticatedUser {
	private final String email;
	private final String applicationName;

	public AuthenticatedUser(String email, String applicationName) {
		this.email = email;
		this.applicationName = applicationName;
	}

	public String getEmail() {
		return email;
	}

	public String getApplicationName() {
		return applicationName;
	}

	@Override
	public String toString() {
		return "AuthenticatedUser{" + "email='" + email + '\'' + ", applicationName='" + applicationName + '\'' + '}';
	}
}

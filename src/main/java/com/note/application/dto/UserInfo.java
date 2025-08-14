package com.note.application.dto;

import java.util.List;
import java.util.Map;

public class UserInfo {
	private int id;
	private String email;
	private String name;
	private String applicationName;
	private List<String> roles;

	public UserInfo() {
	}

	public UserInfo(int id, String email, String name, String applicationName, List<String> roles) {
		this.id = id;
		this.email = email;
		this.name = name;
		this.applicationName = applicationName;
		this.roles = roles;
	}

	public static UserInfo fromMap(Map<String, Object> map) {
		if (map == null)
			return null;
		Integer id = null;
		Object idObj = map.get("id");
		if (idObj instanceof Number)
			id = ((Number) idObj).intValue();
		else if (idObj instanceof String) {
			try {
				id = Integer.parseInt((String) idObj);
			} catch (Exception ignored) {
			}
		}

		String email = map.containsKey("email") ? String.valueOf(map.get("email"))
				: map.containsKey("username") ? String.valueOf(map.get("username")) : null;
		String name = map.containsKey("name") ? String.valueOf(map.get("name")) : null;
		String applicationName = map.containsKey("applicationName") ? String.valueOf(map.get("applicationName"))
				: map.containsKey("application") ? String.valueOf(map.get("application")) : null;

		@SuppressWarnings("unchecked")
		java.util.List<String> roles = map.containsKey("roles") && map.get("roles") instanceof java.util.List
				? (java.util.List<String>) map.get("roles")
				: null;

		return new UserInfo(id, email, name, applicationName, roles);
	}

	// getters & setters

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	@Override
	public String toString() {
		return "UserInfo{id=" + id + ", email='" + email + '\'' + ", name='" + name + '\'' + ", applicationName='"
				+ applicationName + '\'' + ", roles=" + roles + '}';
	}
}

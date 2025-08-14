package com.note.application.service;

import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;

import com.note.application.dto.UserInfo;

@Service
public class CurrentUserService {

	public UserInfo getCurrentUser() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null)
			return null;

		Object principal = auth.getPrincipal();
		if (principal instanceof UserInfo) {
			return (UserInfo) principal;
		}

		return null;
	}
}

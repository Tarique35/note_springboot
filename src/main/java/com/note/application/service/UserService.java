package com.note.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.note.application.entity.User;
import com.note.application.jpa.UserJpa;

@Service
public class UserService {

	@Autowired
	UserJpa userJpa;

	@Autowired
	private PasswordEncoder passwordEncoder;

	public ResponseEntity<?> registerUser(User user) {
		User existingUser = findByEmail(user.getEmail());
		if (existingUser == null) {
			user.setEmail(user.getEmail());
			user.setName(user.getEmail());
			user.setPassword(passwordEncoder.encode(user.getPassword()));
			return ResponseEntity.ok("User Registered successfully");
		}
		return ResponseEntity.ok("User with this email is already present!");
	}

	public User findByEmail(String email) {
		return userJpa.findByEmail(email);
	}

}

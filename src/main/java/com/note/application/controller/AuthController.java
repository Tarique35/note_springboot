package com.note.application.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.note.application.entity.User;
import com.note.application.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@Autowired
	private UserService userService;

//	@PostMapping("/signup")
//	public ResponseEntity<?> registerUser(@RequestBody User user) {
//		User existingUser = userService.findByEmail(user.getEmail());
//		if (existingUser != null) {
//			return ResponseEntity.badRequest().body("Email is already taken");
//		}
//		User newUser = userService.registerUser(user);
//		return ResponseEntity.ok(newUser);
//	}
}

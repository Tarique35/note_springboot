package com.note.application.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.note.application.dto.UserInfo;
import com.note.application.entity.User;
import com.note.application.service.CurrentUserService;
import com.note.application.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final RestTemplate rest;

	@Autowired
	public AuthController(RestTemplate rest) {
		this.rest = rest;
	}

	@Autowired
	private UserService userService;

	@Autowired
	CurrentUserService currentUserService;

	@Value("${jwt.url}")
	private String jwtUrl;

	@GetMapping("/me")
	public ResponseEntity<?> me() {
		UserInfo user = currentUserService.getCurrentUser();
		if (user == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		return ResponseEntity.ok(user);
	}

	@PostMapping("/signup/user")
	public ResponseEntity<?> registerUser(@RequestBody User user) {
		// forward to JWT service signup
		String url = jwtUrl + "/auth/signup";
		System.out.println("Signup url" + url);
		Map<String, Object> body = Map.of("email", user.getEmail(), "password", user.getPassword(), "name",
				user.getName(), "applicationName", "NoteApplication");

		ResponseEntity<String> resp = rest.postForEntity(url, body, String.class);
		return ResponseEntity.status(resp.getStatusCode()).body(resp.getBody());
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
		// forward to JWT service login
		String url = jwtUrl + "/auth/login";
		Map<String, Object> requestBody = Map.of("email", body.get("email"), "password", body.get("password"),
				"applicationName", "NoteApplication");

		ResponseEntity<Map> resp = rest.postForEntity(url, requestBody, Map.class);
		return ResponseEntity.status(resp.getStatusCode()).body(resp.getBody());
	}
}

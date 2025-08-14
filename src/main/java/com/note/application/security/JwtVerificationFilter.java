package com.note.application.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtVerificationFilter implements Filter {

	@Value("${jwt.url}")
	private String jwtUrl;

	private final RestTemplate rest;
	private final Logger logger = LoggerFactory.getLogger(JwtVerificationFilter.class);

	public JwtVerificationFilter(RestTemplate rest) {
		this.rest = rest;
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		String path = request.getRequestURI();

		// Skip token verification for public paths
		if (path.startsWith("/auth") || path.startsWith("/public") || path.startsWith("/images")) {
			chain.doFilter(req, res);
			return;
		}

		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
			return;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", authHeader);
		headers.set("X-Application-Name", "NoteApplication");

		HttpEntity<Void> entity = new HttpEntity<>(headers);

		try {
			// Call JWT microservice verify endpoint
			ResponseEntity<Map> resp = rest.postForEntity(jwtUrl + "/auth/verify", entity, Map.class);

			if (resp.getStatusCode() == HttpStatus.OK) {
				// token is valid according to auth service; now extract username from token
				// payload
				String token = authHeader.substring(7);
				String username = extractUsernameFromJwt(token); // returns null if not found
				String application = extractApplicationFromJwt(token);

				// Build Authentication and set in SecurityContext
				// You can adjust authorities/roles if your JWT returns roles later
//				List<SimpleGrantedAuthority> authorities = Collections
//						.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

				if (username == null) {
					// Fallback principal if token doesn't contain sub
					username = "unknown";
				}
				if (application == null)
					application = "NoteApplication";

				// create typed principal
				AuthenticatedUser principal = new AuthenticatedUser(username, application);

				// grant ROLE_USER by default (you can expand later)
				List<SimpleGrantedAuthority> authorities = Collections
						.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
						null, authorities);

				SecurityContextHolder.getContext().setAuthentication(authentication);
				authentication.setDetails(Map.of("token", token));
				SecurityContextHolder.getContext().setAuthentication(authentication);
				chain.doFilter(req, res);
				return;
			} else {
				logger.info("Token verification endpoint returned non-OK: {}", resp.getStatusCode());
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token verification failed");
				return;
			}
		} catch (Exception e) {
			logger.error("Error while verifying token with auth service at " + jwtUrl, e);
			// Distinguish connection failures
			if (e instanceof org.springframework.web.client.ResourceAccessException) {
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
						"Auth service unavailable: " + e.getMessage());
			} else {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token verification service error");
			}
			return;
		}
	}

	/**
	 * Decode the JWT payload (base64url) and extract the "sub" claim (subject /
	 * username). This does NOT validate signature — we rely on the auth service
	 * /verify for validation.
	 */
	private String extractUsernameFromJwt(String jwt) {
		try {
			String[] parts = jwt.split("\\.");
			if (parts.length < 2)
				return null;

			String payload = parts[1];
			// Base64 URL decoder (handles padding automatically in Java 8+)
			byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
			String json = new String(decodedBytes, StandardCharsets.UTF_8);

			JSONObject obj = new JSONObject(json);
			// standard claim name for username in our tokens is "sub"
			if (obj.has("sub")) {
				return obj.getString("sub");
			}
			// fallback: maybe "username" or "email"
			if (obj.has("username")) {
				return obj.getString("username");
			}
			if (obj.has("email")) {
				System.out.println(obj.getString("email"));
				return obj.getString("email");
			}
			return null;
		} catch (Exception e) {
			logger.error("Failed to extract username from JWT payload", e);
			return null;
		}
	}

	private String extractApplicationFromJwt(String jwt) {
		try {
			String[] parts = jwt.split("\\.");
			if (parts.length < 2)
				return null;
			byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
			String json = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
			org.json.JSONObject obj = new org.json.JSONObject(json);
			if (obj.has("application"))
				return obj.getString("application");
			if (obj.has("app"))
				return obj.getString("app");
			return null;
		} catch (Exception e) {
			logger.error("Failed to extract application from JWT payload", e);
			return null;
		}
	}

}

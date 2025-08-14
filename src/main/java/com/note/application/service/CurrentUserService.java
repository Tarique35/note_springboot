package com.note.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.note.application.dto.UserInfo;

@Service
public class CurrentUserService {

	@Autowired
	private RestTemplate rest;

	@Value("${jwt.url}")
	private String jwtUrl; // e.g. "http://localhost:8081"

	@Value("${application.name:NoteApplication}")
	private String defaultAppName;

	// simple token->cached payload store
	private static record CacheEntry(UserInfo user, long ts) {
	}

	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final long ttlSeconds = 20L;

	/**
	 * Convenience method: returns current user's info or null if unauthenticated /
	 * not found. This is the only call you'll need from controllers/services.
	 */
	public UserInfo getCurrentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null)
			return null;

		// principal can be your AuthenticatedUser or a String (username/email)
		String email = null;
		String application = defaultAppName;

		Object principal = auth.getPrincipal();
		try {
			// If you used the AuthenticatedUser principal earlier
			Class<?> cls = principal.getClass();
			// check fields reflectively to avoid compile-time dependency
			try {
				var getEmail = cls.getMethod("getEmail");
				var getApp = cls.getMethod("getApplicationName");
				Object em = getEmail.invoke(principal);
				Object ap = getApp.invoke(principal);
				if (em != null)
					email = String.valueOf(em);
				if (ap != null)
					application = String.valueOf(ap);
			} catch (NoSuchMethodException ignored) {
				// principal wasn't AuthenticatedUser, fall back
				if (principal instanceof String) {
					email = (String) principal;
				}
			}
		} catch (Exception ex) {
			// fallback simple handling
			if (principal instanceof String)
				email = (String) principal;
		}

		// try to fetch token from auth details (filter sets token in details map if
		// available)
		String token = null;
		try {
			Object details = auth.getDetails();
			if (details instanceof Map) {
				Object t = ((Map<?, ?>) details).get("token");
				if (t != null)
					token = String.valueOf(t);
			} else if (details instanceof String) {
				token = String.valueOf(details);
			}
		} catch (Exception ignored) {
		}

		// Use token as cache key if present (so different tokens for different sessions
		// don't collide)
		String cacheKey = (token != null && !token.isBlank()) ? token : (email + "|" + application);

		// check cache
		var cached = cache.get(cacheKey);
		long now = Instant.now().getEpochSecond();
		if (cached != null && now - cached.ts <= ttlSeconds) {
			return cached.user;
		}

		// Try /auth/verify first (if token available)
		if (token != null && !token.isBlank()) {
			try {
				HttpHeaders headers = new HttpHeaders();
				headers.set("Authorization", "Bearer " + token);
				headers.set("X-Application-Name", application);
				ResponseEntity<Map> resp = rest.exchange(jwtUrl + "/auth/verify", HttpMethod.POST,
						new HttpEntity<>(headers), Map.class);

				if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
					Map<String, Object> body = resp.getBody();
					// if verify returned user info
					if (body.containsKey("email") || body.containsKey("username") || body.containsKey("name")) {
						UserInfo u = UserInfo.fromMap(body);
						cache.put(cacheKey, new CacheEntry(u, now));
						return u;
					}
				}
			} catch (Exception e) {
				// ignore and fall back to userinfo endpoint
			}
		}

		// fallback: if we have email, call /auth/userinfo
		if (email != null && !email.isBlank()) {
			try {
				String url = String.format("%s/auth/userinfo?email=%s&applicationName=%s", jwtUrl,
						java.net.URLEncoder.encode(email, StandardCharsets.UTF_8),
						java.net.URLEncoder.encode(application, StandardCharsets.UTF_8));
				ResponseEntity<Map> userResp = rest.getForEntity(url, Map.class);
				if (userResp.getStatusCode() == HttpStatus.OK && userResp.getBody() != null) {
					UserInfo u = UserInfo.fromMap(userResp.getBody());
					cache.put(cacheKey, new CacheEntry(u, now));
					return u;
				}
			} catch (Exception e) {
				// service down or missing endpoint
			}
		}

		return null;
	}

	/** Force refresh and return fresh user info (skips cache) */
	public UserInfo refreshCurrentUser() {
		// evict then call getCurrentUser
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null)
			return null;
		String token = null;
		try {
			Object details = auth.getDetails();
			if (details instanceof Map)
				token = String.valueOf(((Map<?, ?>) details).get("token"));
			else if (details instanceof String)
				token = (String) details;
		} catch (Exception ignored) {
		}
		String email = auth.getPrincipal() instanceof String ? (String) auth.getPrincipal() : null;
		String application = defaultAppName;
		String cacheKey = (token != null && !token.isBlank()) ? token : (email + "|" + application);
		cache.remove(cacheKey);
		return getCurrentUser();
	}

	/** Evict cache entry for current principal */
	public void evictCurrentUserCache() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null)
			return;
		String token = null;
		try {
			Object details = auth.getDetails();
			if (details instanceof Map)
				token = String.valueOf(((Map<?, ?>) details).get("token"));
			else if (details instanceof String)
				token = (String) details;
		} catch (Exception ignored) {
		}
		String email = auth.getPrincipal() instanceof String ? (String) auth.getPrincipal() : null;
		String cacheKey = (token != null && !token.isBlank()) ? token : (email + "|" + defaultAppName);
		cache.remove(cacheKey);
	}
}

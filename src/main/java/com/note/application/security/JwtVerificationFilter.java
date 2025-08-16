package com.note.application.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import com.note.application.dto.UserInfo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtVerificationFilter extends OncePerRequestFilter {

	private final Logger logger = LoggerFactory.getLogger(JwtVerificationFilter.class);

	@Autowired
	private RestTemplate restTemplate;

	@Value("${jwt.url}")
	private String jwtUrl;

	@Value("${jwt.userinfo-path:/home/me}")
	private String userinfoPath;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		String authHeader = request.getHeader("Authorization");
		logger.debug("JwtVerificationFilter: path={} method={} Authorization-present={}", request.getRequestURI(),
				request.getMethod(), authHeader != null);

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, authHeader);
		// keep the X-Application-Name if your JWT service expects it
		headers.set("X-Application-Name", "NoteApplication");

		HttpEntity<Void> entity = new HttpEntity<>(headers);
		String url = jwtUrl + userinfoPath;

		try {
			logger.debug("JwtVerificationFilter: calling auth service {}", url);
			ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
			logger.debug("JwtVerificationFilter: auth service status {}", resp.getStatusCode());

			if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> body = resp.getBody();
				UserInfo userInfo = UserInfo.fromMap(body);
				if (userInfo != null) {

					List<SimpleGrantedAuthority> authorities = userInfo.getRoles() == null ? List.of()
							: userInfo.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r))
									.collect(Collectors.toList());

					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userInfo, null, authorities);
					SecurityContextHolder.getContext().setAuthentication(authentication);
					logger.debug("JwtVerificationFilter: authentication set for id={}, email={}", userInfo.getId(),
							userInfo.getEmail());
					filterChain.doFilter(request, response);
					return;
				} else {
					logger.warn("JwtVerificationFilter: parsing user info from auth service failed");
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.getWriter().write("Invalid user payload from auth service");
					return;
				}
			} else {
				// forward auth service non-2xx directly to client
				logger.warn("JwtVerificationFilter: auth service returned non-2xx: {}", resp.getStatusCode());
				response.setStatus(resp.getStatusCodeValue());
				if (resp.getBody() != null)
					response.getWriter().write(resp.getBody().toString());
				return;
			}
		} catch (HttpClientErrorException he) {
			// capture upstream 4xx and log body
			logger.warn("JwtVerificationFilter: auth service returned error {} : {}", he.getStatusCode(),
					he.getResponseBodyAsString());
			response.setStatus(he.getStatusCode().value());
			String body = he.getResponseBodyAsString();
			if (body != null && !body.isBlank())
				response.getWriter().write(body);
			return;
		} catch (Exception ex) {
			logger.error("JwtVerificationFilter: unexpected error calling auth service", ex);
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			response.getWriter().write("Auth service unreachable: " + ex.getMessage());
			return;
		}
	}
}

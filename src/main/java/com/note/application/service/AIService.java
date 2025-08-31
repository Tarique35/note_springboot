package com.note.application.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.note.application.dto.ChatResult;
import com.note.application.entity.Note;

@Service
public class AIService {

	@Autowired
	private OllamaChatModel chatModel;

	@Autowired
	@Lazy
	NoteService noteService;

	@Value("${spring.ai.ollama.base-url}")
	private String ollamaUrl;

	private final WebClient webClient;

	public AIService() {
		this.webClient = WebClient.builder().baseUrl(ollamaUrl) // Ollama local server
				.build();
	}

	// ---------------- robust call helper ----------------
	private String callText(String system, String user) {
		String full = (system == null ? "" : system.trim() + "\n\n") + (user == null ? "" : user.trim());
		try {
			Object raw = chatModel.call(full); // may return String or ChatResponse depending on version
			if (raw == null)
				return "";

			if (raw instanceof String) {
				return ((String) raw).trim();
			}

			if (raw instanceof ChatResponse) {
				ChatResponse cr = (ChatResponse) raw;
				try {
					if (cr.getResult() != null && cr.getResult().getOutput() != null) {
						// fallback to toString() on output (safe across versions)
						return cr.getResult().getOutput().toString().trim();
					}
				} catch (Throwable ignored) {
				}
				return cr.toString().trim();
			}

			return raw.toString().trim();
		} catch (Exception e) {
			// optionally log the exception
			return "";
		}
	}

	// ---------------- cleaning helpers ----------------
	private String stripThoughts(String text) {
		if (text == null)
			return "";
		// remove <think> and similar tags and bracketed analysis
		text = text.replaceAll("(?is)<\\/?think.*?>", " ");
		text = text.replaceAll("(?is)\\[.*?\\]", " ");
		text = text.replaceAll("(?is)\\{.*?\\}", " ");
		text = text.replaceAll("\\s+", " ").trim();
		return text;
	}

	/* ---------- helpers ---------- */

	private boolean looksLikeKeywordLine(String line) {
		if (line == null || line.isBlank())
			return false;
		// short, contains commas OR only plausible characters
		// (letters/numbers/spaces/hyphen/commas)
		if (line.length() > 200)
			return false;
		if (!line.matches("^[\\p{L}\\p{N}\\s\\-,]+$"))
			return false;
		// must either contain commas or be short (<=6 tokens)
		return line.contains(",") || line.split("\\s+").length <= 6;
	}

	private boolean looksLikeAnalysis(String text) {
		if (text == null || text.isBlank())
			return false;
		String lower = text.toLowerCase();
		// contains obvious analysis words
		if (lower.contains("i think") || lower.contains("i'm thinking") || lower.contains("let me")
				|| lower.contains("analysis") || lower.contains("first") || lower.contains("second")
				|| lower.contains("therefore")) {
			return true;
		}
		// contains sentences (multiple periods / newlines) and reasonably long
		long sentenceCount = text.split("[\\.\\n]").length;
		if (sentenceCount > 1 && text.length() > 80)
			return true;
		// long single-line verbose output
		if (text.length() > 120)
			return true;
		return false;
	}

	private String[] splitAndCleanKeywords(String line) {
		if (line == null || line.isBlank())
			return new String[0];
		// Allow letters, numbers, hyphens, commas and spaces only
		String cleaned = line.replaceAll("[^\\p{L}\\p{N},\\-\\s]", " ").trim();
		String[] parts = cleaned.split("\\s*,\\s*");
		return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).map(this::shortenPhrase) // normalize
				.toArray(String[]::new);
	}

	private String[] heuristicKeywordsFromQuery(String query) {
		if (query == null)
			return new String[0];
		String cleaned = query.replaceAll("[^\\p{L}\\p{N}\\s\\-]", " ").toLowerCase().trim();
		String[] tokens = cleaned.split("\\s+");

		// stopwords - extend as needed
		Set<String> stop = Set.of("is", "are", "the", "a", "an", "for", "of", "in", "on", "to", "do", "does", "did",
				"there", "i", "my", "me", "with", "about", "that", "this");

		// collect candidate tokens of length>2
		List<String> candidates = Arrays.stream(tokens).filter(t -> t.length() > 2 && !stop.contains(t))
				.collect(Collectors.toList());

		// Build short phrases (bi-grams) as well from adjacent tokens to catch 'jwt
		// filter' etc.
		LinkedHashSet<String> phrases = new LinkedHashSet<>();
		for (int i = 0; i < candidates.size(); i++) {
			String w = candidates.get(i);
			phrases.add(w);
			if (i + 1 < candidates.size()) {
				String bi = w + " " + candidates.get(i + 1);
				phrases.add(bi);
			}
			// also tri-grams optionally
			if (i + 2 < candidates.size()) {
				String tri = w + " " + candidates.get(i + 1) + " " + candidates.get(i + 2);
				phrases.add(tri);
			}
			if (phrases.size() >= 8)
				break;
		}

		return phrases.stream().map(this::shortenPhrase).limit(6).toArray(String[]::new);
	}

	private String shortenPhrase(String s) {
		if (s == null)
			return "";
		// trim and keep up to 3 words
		String[] toks = s.trim().split("\\s+");
		if (toks.length <= 3)
			return String.join(" ", toks);
		return String.join(" ", Arrays.copyOfRange(toks, 0, 3));
	}

	private String[] limitAndUnique(String[] arr, int limit) {
		if (arr == null)
			return new String[0];
		LinkedHashSet<String> set = new LinkedHashSet<>();
		for (String s : arr) {
			if (s == null)
				continue;
			String t = s.trim().toLowerCase();
			if (t.isEmpty())
				continue;
			set.add(t);
			if (set.size() >= limit)
				break;
		}
		return set.toArray(new String[0]);
	}

	// ---------------- improved extractKeywordsFromQuery ----------------
	public String[] extractKeywordsFromQuery(String query) {
		// 1) Strict LLM attempt
		String system = """
				You are a keyword extractor. RULES:
				- Output ONLY a single line of keywords or short phrases, comma-separated.
				- No explanations, no analysis, no tags, no extra text.
				- Keep phrases short (1-3 words). Prefer nouns or noun phrases.
				""";

		String user = "Query: \"" + query + "\"\n\nOutput (comma-separated keywords only):";

		String raw = callText(system, user);
		if (raw == null)
			raw = "";
		raw = stripThoughts(raw).trim();

		// 2) If LLM produced a compact keyword-like line -> parse and return
		String firstLine = raw.split("\\R", 2)[0].trim();
		if (looksLikeKeywordLine(firstLine)) {
			String[] kws = splitAndCleanKeywords(firstLine);
			if (kws.length > 0)
				return limitAndUnique(kws, 6);
		}

		// 3) If LLM output looks like analysis (long text, sentences, 'I', 'think',
		// etc.) -> DO NOT re-prompt with that analysis.
		if (looksLikeAnalysis(raw)) {
			// deterministic heuristic fallback using original query
			String[] heuristic = heuristicKeywordsFromQuery(query);
			return limitAndUnique(heuristic, 6);
		}

		// 4) Edge: LLM returned something unexpected (short single word maybe)
		String[] maybe = splitAndCleanKeywords(firstLine);
		if (maybe.length > 0)
			return limitAndUnique(maybe, 6);

		// 5) Final fallback: heuristic
		return limitAndUnique(heuristicKeywordsFromQuery(query), 6);
	}

	// generate natural language answer based on notes context
	public String generateAnswer(String query, String notesContext) {
		String answerPrompt = """
				You are an assistant that MUST answer using only the provided notes below.
				Do NOT hallucinate or add outside information. If notes don't contain the answer, reply:
				"I could not find this in your notes."

				Notes Context:
				%s

				User Question: %s

				Answer in clear, concise natural language and mention which note title you referenced if helpful.
				""".formatted(notesContext, query);

		return chatModel.call(answerPrompt).trim();
	}

	// --- Intent classification ---
	public String classifyIntent(String query) {
		String system = """
				You are an intent classifier. The user has private notes.
				RULES:
				- Reply with exactly one word: NOTES or GENERAL (uppercase).
				- No extra text or explanation.
				- If unsure, reply GENERAL.
				""";

		String user = "Query: \"" + query + "\"\n\nReply with NOTES or GENERAL only.";

		String raw = callText(system, user);
		// Defensive cleaning & extraction
		if (raw == null || raw.isBlank())
			return "GENERAL";
		raw = raw.replaceAll("(?is)<\\/?think.*?>", " ").replaceAll("\\s+", " ").trim();
		if (raw.toUpperCase().contains("NOTES"))
			return "NOTES";
		return "GENERAL";
	}

	// --- General AI chat ---
	public String chatGeneral(String query) {
		// If you want to enforce no chain-of-thought for general chat too, you can use
		// a system message.
		// For shortest path, we call directly:
		return chatModel.call(query).trim();
	}

	public ChatResult handleUserQuery(String query, int userId) {
		String intent = classifyIntent(query);

		if ("NOTES".equals(intent)) {
			String[] keywords = extractKeywordsFromQuery(query);

			List<Note> matchedNotes = noteService.searchNotesByKeywords(keywords, userId);

			if (matchedNotes == null || matchedNotes.isEmpty()) {
				return new ChatResult(intent, "I could not find this in your notes.", List.of());
			}

			// build concise context (title + content). limit size if needed in production
			String notesContext = matchedNotes.stream()
					.map(n -> "Title: " + (n.getTitle() == null ? "Untitled" : n.getTitle()) + "\nContent: "
							+ (n.getContent() == null ? "" : n.getContent()))
					.collect(Collectors.joining("\n\n---\n\n"));

			String answer = generateAnswer(query, notesContext);
			return new ChatResult(intent, answer, matchedNotes);
		} else {
			// general question -> direct LLM response
			String answer = chatModel.call(query).trim();
			return new ChatResult(intent, answer, null);
		}
	}

}

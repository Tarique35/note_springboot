package com.note.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
//	private String callText(String system, String user) {
//		String full = (system == null ? "" : system.trim() + "\n\n") + (user == null ? "" : user.trim());
//		try {
//			Object raw = chatModel.call(full); // many impls accept single string
//			if (raw == null)
//				return "";
//			if (raw instanceof String)
//				return ((String) raw).trim();
//			if (raw instanceof ChatResponse) {
//				ChatResponse cr = (ChatResponse) raw;
//				try {
//					if (cr.getResult() != null && cr.getResult().getOutput() != null) {
//						return cr.getResult().getOutput().toString().trim();
//					}
//				} catch (Throwable ignored) {
//				}
//				return cr.toString().trim();
//			}
//			return raw.toString().trim();
//		} catch (Exception e) {
//			// optional: log
//			return "";
//		}
//	}

	private String callText(Object modelInput) {
		try {
			Object raw;
			if (modelInput instanceof String s) {
				raw = chatModel.call(s);
			} else if (modelInput instanceof org.springframework.ai.chat.prompt.Prompt p) {
				raw = chatModel.call(p);
			} else {
				throw new IllegalArgumentException("Unsupported input type: " + modelInput.getClass());
			}

			if (raw == null)
				return "";

			if (raw instanceof String s)
				return s.trim();

			if (raw instanceof org.springframework.ai.chat.model.ChatResponse cr) {
				if (cr.getResult() != null && cr.getResult().getOutput() != null) {
					return cr.getResult().getOutput().toString().trim();
				}
				return cr.toString().trim();
			}
			return raw.toString().trim();

		} catch (Exception e) {
			return "";
		}
	}

	// ---- Overload: system + user messages ----
	private String callText(String system, String user) {
		var prompt = new org.springframework.ai.chat.prompt.Prompt(
				java.util.List.of(new org.springframework.ai.chat.messages.SystemMessage(system),
						new org.springframework.ai.chat.messages.UserMessage(user)));
		return callText(prompt); // delegate to Object version
	}

	// ---------------- strip thinking / cleanup ----------------
	private String stripThoughts(String s) {
		if (s == null)
			return "";
		// Remove explicit tags and obvious analysis fragments
		s = s.replaceAll("(?is)<\\/?think.*?>", " ");
		s = s.replaceAll("(?is)\\[.*?\\]", " ");
		s = s.replaceAll("(?is)\\{.*?\\}", " ");
		s = s.replaceAll("(?i)okay[,]? so .*?\\.", " "); // crude remove of "Okay, so I..."
		s = s.replaceAll("\\s+", " ").trim();
		return s;
	}

	private String extractFinalAnswer(String raw) {
		if (raw == null || raw.isBlank())
			return "";
		// If model accidentally left a thought block, try to get text AFTER the last
		// closing tag
		int idx = raw.lastIndexOf("</think>");
		if (idx != -1 && idx + 8 < raw.length()) {
			String after = raw.substring(idx + 8).trim();
			if (!after.isBlank())
				return after;
		}
		// If no tag, remove reasoning and return the remaining first/last meaningful
		// sentence.
		String cleaned = stripThoughts(raw);
		// If cleaned contains multiple paragraphs, prefer the last short paragraph
		// (often final answer)
		String[] parts = cleaned.split("\\n\\s*\\n");
		for (int i = parts.length - 1; i >= 0; i--) {
			String p = parts[i].trim();
			if (p.length() > 0 && p.length() < 1000) {
				// if it still looks like analysis, try to pick the final sentence
				String[] sents = p.split("(?<=[.!?])\\s+");
				if (sents.length > 0)
					return sents[sents.length - 1].trim();
			}
		}
		// fallback to whole cleaned string
		return cleaned;
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

	private String escapeJson(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
	}

	private String callModel(String fullPrompt) {
		try {
			Object raw = chatModel.call(fullPrompt); // may return String or ChatResponse
			if (raw == null)
				return "";
			if (raw instanceof String)
				return ((String) raw).trim();
			if (raw instanceof org.springframework.ai.chat.model.ChatResponse) {
				var cr = (org.springframework.ai.chat.model.ChatResponse) raw;
				try {
					if (cr.getResult() != null && cr.getResult().getOutput() != null)
						return cr.getResult().getOutput().toString().trim();
				} catch (Throwable ignored) {
				}
				return cr.toString().trim();
			}
			return raw.toString().trim();
		} catch (Exception e) {
			// optional: log error
			return "";
		}
	}

	// ---------------- improved extractKeywordsFromQuery ----------------
	public String[] extractKeywordsFromQuery(String query) {
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

		String firstLine = raw.split("\\R", 2)[0].trim();
		if (looksLikeKeywordLine(firstLine)) {
			String[] kws = splitAndCleanKeywords(firstLine);
			if (kws.length > 0)
				return limitAndUnique(kws, 6);
		}

		if (looksLikeAnalysis(raw)) {
			return limitAndUnique(heuristicKeywordsFromQuery(query), 6);
		}

		String[] maybe = splitAndCleanKeywords(firstLine);
		if (maybe.length > 0)
			return limitAndUnique(maybe, 6);

		return limitAndUnique(heuristicKeywordsFromQuery(query), 6);
	}

	public List<Note> getRelevantNotes(String query, List<Note> allNotes) {
		String[] keywords = extractKeywordsFromQuery(query);
		List<Note> relevant = new ArrayList<>();

		for (Note note : allNotes) {
			String combined = note.getTitle() + " " + note.getContent();
			for (String kw : keywords) {
				if (combined.toLowerCase().contains(kw.toLowerCase())) {
					relevant.add(note);
					break;
				}
			}
		}
		return relevant;
	}

	// ---------------- generateAnswer: strict no-think prompt ----------------
	/**
	 * New generateAnswer: forces the model to think internally but ONLY return a
	 * final answer. Uses few-shot examples to teach the model the desired output
	 * format (final-answer-only).
	 */
//	public String generateAnswer(String query, List<Note> matchedNotes) {
//		String notesContext = buildNotesContext(matchedNotes);
//
//		// Strict system message: no chain-of-thought, output only final answer
//		String system = """
//				You are an assistant that MUST answer using ONLY the provided notes.
//				IMPORTANT RULES (MUST FOLLOW):
//				1) Do NOT reveal your chain-of-thought or any analysis.
//				2) Output ONLY the final concise answer (1-3 sentences). No extra commentary.
//				3) If the notes do not contain the answer, output exactly: I could not find this in your notes.
//				4) If referencing a note, you may briefly indicate the note title in parentheses, e.g. (Title: XYZ).
//				""";
//
//		// Few-shot example (one example) to teach format
//		String example = """
//				Example:
//				Notes:
//				Title: Shopping
//				Content: Buy milk and bread.
//
//				Question: "Do I have milk on my shopping list?"
//				Correct output: Yes — your Shopping note lists milk. (Title: Shopping)
//
//				Notes:
//				Title: Random
//				Content: asdfg qwerty
//
//				Question: "How do I fix the login error?"
//				Correct output: I could not find this in your notes.
//				""";
//
//		String user = "Notes:\n" + notesContext + "\n\nQuestion: " + query
//				+ "\n\nProduce ONLY the final answer (1-3 sentences). Follow the examples above exactly.";
//
//		String raw = callText(system + "\n\n" + example, user);
//		String cleaned = stripThoughts(raw);
//
//		// If model still returns multi-paragraph, prefer the last concise
//		// paragraph/sentence
//		if (cleaned.contains("\n\n")) {
//			String[] parts = cleaned.split("\\n\\s*\\n");
//			// pick the shortest non-empty chunk from end
//			for (int i = parts.length - 1; i >= 0; i--) {
//				String p = parts[i].trim();
//				if (!p.isEmpty() && p.length() < 1000) {
//					cleaned = p;
//					break;
//				}
//			}
//		}
//
//		// Final filter: remove phrases that indicate internal reasoning leaked
//		String low = cleaned.toLowerCase();
//		if (low.contains("i think") || low.contains("let me") || low.contains("analysis") || low.contains("first")) {
//			// as a last-resort safe output, prefer the strict fallback
//			return "I could not find this in your notes.";
//		}
//		if (cleaned.isBlank())
//			return "I could not find this in your notes.";
//		return cleaned;
//	}

	public String generateAnswer(String query, List<Note> matchedNotes) {
		if (matchedNotes == null || matchedNotes.isEmpty()) {
			return "I could not find this in your notes.";
		}

		// Build JSON-like array so model sees each note as one unit
		StringBuilder json = new StringBuilder();
		json.append("[");
		for (int i = 0; i < matchedNotes.size(); i++) {
			Note n = matchedNotes.get(i);
			String title = n.getTitle() == null ? "Untitled" : n.getTitle();
			String content = n.getContent() == null ? "" : n.getContent();
			// escape quotes/newlines simply for prompt readability
			title = title.replace("\"", "\\\"");
			content = content.replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
			json.append("{\"title\":\"").append(title).append("\",\"content\":\"").append(content).append("\"}");
			if (i < matchedNotes.size() - 1)
				json.append(",");
		}
		json.append("]");

		String system = """
				You are a concise assistant that MUST answer using ONLY the provided notes.
				RULES (MUST FOLLOW):
				1) Do NOT output any chain-of-thought or internal reasoning.
				2) Treat each JSON object as one note (title + content). Do NOT invent or assume other notes.
				3) For each matched note, produce a one-line human-friendly summary (1-2 short sentences) that
				   either (a) summarizes the content, or (b) if the content appears to be non-meaningful random characters,
				   say: "Content appears non-meaningful; excerpt: \"<first 40 chars>\"".
				4) After listing summaries for matched notes, output a single final sentence answering the user question directly
				   (e.g. "Yes — you have a note titled Testing that seems related to 'test'.").
				5) If none of the notes answer the question, output exactly: I could not find this in your notes.
				6) Output MUST be compact: the summaries followed by one final answer sentence. No extra text.
				""";

		String examples = """
				Example 1:
				Notes JSON: [{"title":"Shopping","content":"Buy milk and bread."}]
				Question: "Do I have milk on my shopping list?"
				Correct output:
				Shopping: Your Shopping note lists milk and bread. (Content: "Buy milk and bread.")
				Final: Yes — your Shopping note lists milk. (Title: Shopping)

				Example 2:
				Notes JSON: [{"title":"Random","content":"asdjf98 234!#$"}]
				Question: "How to fix login error?"
				Correct output:
				Random: Content appears non-meaningful; excerpt: "asdjf98 234!#".
				Final: I could not find this in your notes.
				""";

		String user = "NOTES_JSON: " + json.toString() + "\n\nQuestion: " + query
				+ "\n\nProduce ONLY: (a) one-line summary for each note, then (b) one final answer sentence. Follow the rules above.";

		String raw = callText(system + "\n\n" + examples, user);
		String cleaned = stripThoughts(raw);

		// If model output contains multiple blank-separated blocks, prefer taking them
		// all but ensure final sentence present.
		// Defensive: if model still outputs reasoning, fallback to safe phrasing
		// referencing titles
		String low = cleaned.toLowerCase();
		if (cleaned.isBlank() || low.contains("i think") || low.contains("let me") || low.contains("analysis")
				|| low.contains("step")) {
			// fallback: politely state found titles and let user open them
			String titles = matchedNotes.stream().map(n -> n.getTitle() == null ? "Untitled" : n.getTitle()).distinct()
					.collect(Collectors.joining(", "));
			return "Yes — you have note(s): " + titles + ". Would you like to open them?";
		}

		// final cleanup: ensure we end with a final answer sentence; if not, append one
		// referencing titles
		// detect if cleaned already contains a "Final:" line from examples pattern
		if (cleaned.contains("Final:")) {
			// remove "Final:" tokens and return neatly
			String out = Arrays.stream(cleaned.split("\\R")).map(String::trim).filter(s -> !s.isEmpty())
					.collect(Collectors.joining("\n"));
			out = out.replaceAll("(?m)^Final:\\s*", "");
			return out.trim();
		}

		// If user wants a direct single paragraph: ensure last sentence is final
		// answer.
		// We'll return the whole cleaned output (summaries + final answer) as the AI's
		// response.
		return cleaned.trim();
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
//			String notesContext = matchedNotes.stream()
//					.map(n -> "Title: " + (n.getTitle() == null ? "Untitled" : n.getTitle()) + "\nContent: "
//							+ (n.getContent() == null ? "" : n.getContent()))
//					.collect(Collectors.joining("\n\n---\n\n"));
//
//			String answer = generateAnswer(query, notesContext);

			String notesContext = buildNotesContext(matchedNotes); // uses builder above
			String answer = generateAnswer(query, matchedNotes); // returns clean answer
			return new ChatResult(intent, answer, matchedNotes);
		} else {
			// general question -> direct LLM response
			String answer = chatModel.call(query).trim();
			return new ChatResult(intent, answer, null);
		}
	}

	// ---------------- helper to build notesContext from List<Note>
	// ----------------
	/**
	 * Build a compact notesContext from matched notes (title + content). Trims
	 * content length for safety. Caller can adjust limit.
	 */
	private String buildNotesContext(List<Note> notes) {
		if (notes == null || notes.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		for (Note n : notes) {
			String t = n.getTitle() == null ? "Untitled" : n.getTitle();
			String c = n.getContent() == null ? "" : n.getContent();
			if (c.length() > 1500)
				c = c.substring(0, 1500) + "...";
			sb.append("Note:\nTitle: ").append(t).append("\nContent: ").append(c).append("\n\n");
		}
		return sb.toString().trim();
	}

	private String allowedTitlesCsv(List<Note> notes) {
		if (notes == null || notes.isEmpty())
			return "";
		return notes.stream().map(n -> n.getTitle() == null ? "Untitled" : n.getTitle()).distinct()
				.map(s -> "\"" + s.replace("\"", "\\\"") + "\"").collect(Collectors.joining(", "));
	}

	private boolean isAcceptableAnswer(String ans, List<Note> matchedNotes) {
		if (ans == null)
			return false;
		String a = ans.trim();
		if (a.isEmpty())
			return false;

		String low = a.toLowerCase();
		// avoid chain-of-thought phrases
		if (low.contains("i think") || low.contains("let me") || low.contains("first") || low.contains("analysis")
				|| low.contains("step by step") || low.contains("okay")) {
			return false;
		}
		// length guard
		if (a.length() > 1000)
			return false;

		// Titles check: if ans mentions any title not in matchedNotes -> reject
		List<String> allowed = matchedNotes.stream().map(n -> n.getTitle() == null ? "" : n.getTitle().toLowerCase())
				.distinct().collect(Collectors.toList());

		// find any quoted or parenthesized Title: patterns
		Pattern p = Pattern.compile("\\(Title:\\s*([^\\)]+)\\)", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(a);
		while (m.find()) {
			String mentioned = m.group(1).trim().toLowerCase();
			if (!allowed.contains(mentioned))
				return false;
		}

		// Also check for "titled X" or "title X" occurrences — simple substring check
		// for safety:
		for (String token : allowed) {
			// remove token occurrences from answer so leftover words won't fool us
			a = a.replaceAll("(?i)\\b" + Pattern.quote(token) + "\\b", "");
		}
		// if answer still contains the word "title" or "titled" followed by something,
		// we conservatively reject
		if (a.matches("(?i).*\\b(title|titled)\\b.*"))
			return false;

		return true;
	}
}

package com.note.application.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.note.application.dto.ChatResult;
import com.note.application.dto.UserInfo;
import com.note.application.entity.Note;
import com.note.application.service.OldAIService;
import com.note.application.service.CurrentUserService;
import com.note.application.service.OldNoteService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class OldChatController {

	private final OllamaChatModel chatModel;

	@Autowired
	CurrentUserService currentUserService;

	@Autowired
	private OldNoteService noteService;

	@Autowired
	private OldAIService aiService;

	@Autowired
	public OldChatController(OllamaChatModel chatModel) {
		this.chatModel = chatModel;
	}

	public int getCurrentUserId() {
		UserInfo userInfo = currentUserService.getCurrentUser();
		int currentUserId = userInfo.getId();
		return currentUserId;
	}

	@GetMapping("/generate")
	public Map<String, String> generate(
			@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
		return Map.of("generation", this.chatModel.call(message));
	}

	@GetMapping("/generateStream")
	public Flux<ChatResponse> generateStream(
			@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
		Prompt prompt = new Prompt(new UserMessage(message));
		return this.chatModel.stream(prompt);
	}

	@GetMapping("/intent")
	public Map<String, String> classifyIntent(@RequestParam("query") String query) {
		String classifierPrompt = """
				You are an intent classifier.
				The user has a set of private notes.
				Decide if the query is about their personal notes or a general question.

				Query: "%s"

				Answer with only one word: NOTES or GENERAL.
				""".formatted(query);

		String result = chatModel.call(classifierPrompt).trim();

		return Map.of("intent", result);
	}

//	@PostMapping("/ask")
//	public ResponseEntity<List<Note>> askNotes(@RequestBody String json, Principal principal) {
//		JSONObject obj = new JSONObject(json);
//		String query = obj.getString("query");
//
//		int userId = getCurrentUserId();
//		List<Note> results = noteService.searchNotesWithAI(query, userId);
//
//		return ResponseEntity.ok(results);
//	}

	@PostMapping("/chat")
	public ResponseEntity<ChatResult> chatWithNotes(@RequestBody String json, Principal principal) {
		JSONObject obj = new JSONObject(json);
		String query = obj.getString("query");

		int userId = getCurrentUserId();
		ChatResult result = aiService.handleUserQuery(query, userId);

		return ResponseEntity.ok(result);
	}

	@PostMapping("/test")
	public ResponseEntity<?> test(@RequestBody String json) {
		return ResponseEntity.ok(json);
	}
}

package com.note.application.service;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.note.application.dto.UserInfo;
import com.note.application.entity.Note;
import com.note.application.jpa.NoteJpa;
import com.note.application.jpa.UserJpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class NoteService {

	@Autowired
	NoteJpa noteJpa;

	@Autowired
	UserJpa userJpa;

	@Autowired
	CurrentUserService currentUserService;

	@Autowired
	AIService aiService;

	@PersistenceContext
	EntityManager entityManager;

//	public Note createNewNote(Note json) {
//		return addNote(json);
//	}

	public int getCurrentUserId() {
		UserInfo userInfo = currentUserService.getCurrentUser();
		int currentUserId = userInfo.getId();
		return currentUserId;
	}

	public Note addNote(Note json) {
		int userId = getCurrentUserId();
		json.setUserId(userId);
//		if (json.getTitle() == null || json.getTitle().isBlank()) {
//			return null; // or throw exception / return ResponseEntity
//		}
//		if (json.getContent() == null || json.getContent().isBlank()) {
//			return null;
//		}
		noteJpa.save(json);
		return json;
	}

	public List<Note> getAllNotesOfUser() {
//		User user = userJpa.findByEmail("tarique@gmail.com");
//		System.out.println(userInfo);
		int userId = getCurrentUserId();
		deleteEmptyNotes(userId);
		return noteJpa.getAllNotesOfUser(userId);
	}

	public Note getSelectedNote(String json) {
		JSONObject jsonObj = new JSONObject(json);
		int noteId = jsonObj.getInt("id");
		int userId = getCurrentUserId();
		return noteJpa.getSelectedNote(noteId, userId);
	}

	public Note updateExistingNote(String json) {
		JSONObject jsonObj = new JSONObject(json);
		int noteId = jsonObj.getInt("id");
		int userId = getCurrentUserId();
		Note note = noteJpa.getSelectedNote(noteId, userId);
		JSONObject jsonObj2 = new JSONObject(json);
		String title = jsonObj2.getString("title");
		String content = jsonObj2.getString("content");

		note.setTitle(title);
		note.setContent(content);
		noteJpa.update(note);

		return null;
	}

	@Transactional
	public void deleteEmptyNotes(int userId) {
		List<Note> noteList = noteJpa.getAllEmptyNotes(userId);
		for (Note note : noteList) {
			Note managed = entityManager.contains(note) ? note : entityManager.merge(note);
			entityManager.remove(managed);
		}
	}

	public Note bookmark(String json) {
		JSONObject jsonObj = new JSONObject(json);
		int noteId = jsonObj.getInt("id");
		int userId = getCurrentUserId();
		Note note = noteJpa.getSelectedNote(noteId, userId);
		Boolean isBookmarked = note.getBookmarked();
		if (isBookmarked == null) {
			note.setBookmarked(true);
		} else {
			note.setBookmarked(!isBookmarked);
		}
		noteJpa.update(note);
		return note;
	}

	public List<Note> getUserBookmarks(String json) {
		int userId = getCurrentUserId();
		return noteJpa.getUserBookmarks(userId);
	}

	public ResponseEntity<String> deleteNote(String json) {
		try {
			JSONObject jsonObj = new JSONObject(json);
			int id = jsonObj.getInt("id");
			Note note = noteJpa.findById(id);
			if (note == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found");
			}
			noteJpa.delete(note);
			return ResponseEntity.ok("Note deleted successfully");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error deleting note: " + e.getMessage());
		}
	}

//	public List<Note> searchNotesWithAI(String query, int userId) {
//		// Step 1: Ask Ollama (DeepSeek) to extract relevant keyword(s)
//		String keyword = aiService.extractKeywordFromQuery(query);
//
//		// Step 2: Search user’s notes with that keyword
//		return noteJpa.findRelevantNotes(userId, keyword);
//	}

	public String askNotes(String query, int userId) {
		// Step 1: Ask AI for keywords
		String[] keywords = aiService.extractKeywordsFromQuery(query);

		// Step 2: Search notes for each keyword
		List<Note> relevantNotes = new ArrayList<>();
		for (String kw : keywords) {
			relevantNotes.addAll(noteJpa.findRelevantNotes(userId, kw));
		}

		// Remove duplicates
		relevantNotes = relevantNotes.stream().distinct().toList();

		// Step 3: If no notes found
		if (relevantNotes.isEmpty()) {
			return "No relevant notes found.";
		}

		// Step 4: Combine notes into context
		StringBuilder contextBuilder = new StringBuilder();
		for (Note n : relevantNotes) {
			contextBuilder.append("Title: ").append(n.getTitle()).append("\n");
			contextBuilder.append("Content: ").append(n.getContent()).append("\n\n");
		}

		// Step 5: Ask AI for natural language answer
		return aiService.generateAnswer(query, contextBuilder.toString());
	}

	public List<Note> searchNotesByKeywords(String[] keywords, int userId) {
		List<Note> result = new ArrayList<>();
		if (keywords == null || keywords.length == 0) {
			return result;
		}

		for (String kw : keywords) {
			if (kw == null || kw.trim().isEmpty())
				continue;
			List<Note> found = noteJpa.findRelevantNotes(userId, kw.trim());
			for (Note n : found) {
				boolean exists = result.stream().anyMatch(r -> r.getId() == n.getId());
				if (!exists)
					result.add(n);
			}
		}

		return result;
	}
}

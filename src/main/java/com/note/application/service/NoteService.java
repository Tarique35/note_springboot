package com.note.application.service;

import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.note.application.dto.UserInfo;
import com.note.application.entity.Note;
import com.note.application.entity.User;
import com.note.application.jpa.NoteJpa;
import com.note.application.jpa.UserJpa;

@Service
public class NoteService {

	@Autowired
	NoteJpa noteJpa;

	@Autowired
	UserJpa userJpa;

	@Autowired
	CurrentUserService currentUserService;

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

	public void deleteEmptyNotes(int userId) {
		List<Note> noteList = noteJpa.getAllEmptyNotes(userId);
		for (Note note : noteList) {
			noteJpa.delete(note);
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
}

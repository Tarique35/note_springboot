package com.note.application.service;

import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

//	public Note createNewNote(Note json) {
//		return addNote(json);
//	}

	public Note addNote(Note json) {
		User user = userJpa.findByEmail("tarique@gmail.com");
		json.setUserId(user);
		noteJpa.save(json);
		return json;
	}

	public List<Note> getAllNotesOfUser() {
		User user = userJpa.findByEmail("tarique@gmail.com");
		deleteEmptyNotes();
		return noteJpa.getAllNotesOfUser(user);
	}

	public Note getSelectedNote(String json) {
		return noteJpa.getSelectedNote(json);
	}

	public Note updateExistingNote(String json) {
		Note note = noteJpa.getSelectedNote(json);
		JSONObject jsonObj = new JSONObject(json);
		String title = jsonObj.getString("title");
		String content = jsonObj.getString("content");

		note.setTitle(title);
		note.setContent(content);
		noteJpa.update(note);

		return null;
	}

	public void deleteEmptyNotes() {
		List<Note> noteList = noteJpa.getAllEmptyNotes();
		for (Note note : noteList) {
			noteJpa.delete(note);
		}
	}

	public Note bookmark(String json) {
		Note note = noteJpa.getSelectedNote(json);
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
		return noteJpa.getUserBookmarks(json);
	}
}

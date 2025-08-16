package com.note.application.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.note.application.dto.UserInfo;
import com.note.application.entity.Note;
import com.note.application.entity.User;
import com.note.application.service.CurrentUserService;
import com.note.application.service.NoteService;

@RestController
@CrossOrigin(origins = "*")
public class NotesController {

	@Autowired
	NoteService noteService;

	@Autowired
	CurrentUserService currentUserService;

//	@PostMapping("/note/createnew")
//	public Note createNewNote(@RequestBody Note Json)
//	{
//		return noteService.createNewNote(Json);
//	}

	@PostMapping("/save/note")
	public Note addNote(@RequestBody Note json) {
		return noteService.addNote(json);
	}

	@PostMapping("/all/notes")
	public List<Note> getAllNotesOfUser() {
		return noteService.getAllNotesOfUser();
	}

	@PostMapping("selected/note")
	public Note getSelectedNote(@RequestBody String json) {
		return noteService.getSelectedNote(json);
	}

	@PostMapping("update/existingnote")
	public Note updateExistingNote(@RequestBody String json) {
		return noteService.updateExistingNote(json);
	}

	@PostMapping("/bookmark")
	public Note bookmark(@RequestBody String json) {
		return noteService.bookmark(json);
	}

	@PostMapping("/get/bookmarks")
	public List<Note> getUserBookmarks(@RequestBody String json) {
		return noteService.getUserBookmarks(json);
	}

	@PostMapping("/deletenote")
	public ResponseEntity<String> deleteNote(@RequestBody String json) {
		return noteService.deleteNote(json);
	}

	@GetMapping("/working")
	public String working() {
		return "api is working";
	}

	@GetMapping("/currentuser")
	public ResponseEntity<?> currentUser() {
		UserInfo u = currentUserService.getCurrentUser();
		if (u == null)
			return ResponseEntity.status(401).body("Unauthorized");
		return ResponseEntity.ok(u);
	}

	@GetMapping("/admin/check")
	public ResponseEntity<?> adminCheck() {
		return ResponseEntity.ok("Admin only");
	}
}

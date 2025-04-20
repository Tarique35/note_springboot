package com.note.application.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.note.application.entity.Note;
import com.note.application.service.NoteService;

@RestController
@CrossOrigin(origins = "*")
public class NotesController {

	@Autowired
	NoteService noteService;

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
	public Note bookmark(@RequestBody String json)
	{
		return noteService.bookmark(json);
	}
}

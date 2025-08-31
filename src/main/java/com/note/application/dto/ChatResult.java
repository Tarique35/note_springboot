package com.note.application.dto;

import java.util.List;

import com.note.application.entity.Note;

public class ChatResult {
	private String intent;
	private String answer;
	private List<Note> matchedNotes;

	public ChatResult() {
	}

	public ChatResult(String intent, String answer, List<Note> matchedNotes) {
		this.intent = intent;
		this.answer = answer;
		this.matchedNotes = matchedNotes;
	}

	public String getIntent() {
		return intent;
	}

	public void setIntent(String intent) {
		this.intent = intent;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public List<Note> getMatchedNotes() {
		return matchedNotes;
	}

	public void setMatchedNotes(List<Note> matchedNotes) {
		this.matchedNotes = matchedNotes;
	}
}

package com.note.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Note {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	private String title;
	private String content;
	private Boolean bookmarked;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User userId;

	public Note() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Note(int id, String title, String content, User userId, Boolean bookmarked) {
		super();
		this.id = id;
		this.title = title;
		this.content = content;
		this.userId = userId;
		this.bookmarked = bookmarked;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public User getUserId() {
		return userId;
	}

	public void setUserId(User userId) {
		this.userId = userId;
	}

	public Boolean getBookmarked() {
		return bookmarked;
	}

	public void setBookmarked(Boolean bookmarked) {
		this.bookmarked = bookmarked;
	}

	@Override
	public String toString() {
		return "Note [id=" + id + ", title=" + title + ", content=" + content + ", bookmarked=" + bookmarked
				+ ", userId=" + userId + "]";
	}

}

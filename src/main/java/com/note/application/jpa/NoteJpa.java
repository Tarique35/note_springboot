package com.note.application.jpa;

import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.note.application.entity.Note;
import com.note.application.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Repository
@Transactional
public class NoteJpa {

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	UserJpa userJpa;

	public void save(Note note) {
		entityManager.persist(note);
	}

	public void update(Note note) {
		entityManager.merge(note);
	}

	public Note findById(int id) {
		return entityManager.find(Note.class, id);
	}

	public List<Note> findAll() {
		return entityManager.createQuery("Select n from Note n", Note.class).getResultList();
	}

	public void delete(Note note) {
		entityManager.remove(note);
	}

	public List<Note> getAllNotesOfUser(User user) {
//		String user_id = user.getId();
		return entityManager.createQuery("Select n from Note n where userId=:userId", Note.class)
				.setParameter("userId", user).getResultList();
	}

	public Note getSelectedNote(String json) {
		User user = userJpa.findByEmail("tarique@gmail.com"); // Assuming you want to get the user first
		JSONObject jsonObj = new JSONObject(json);
		int noteId = jsonObj.getInt("id");
		return entityManager.createQuery("SELECT n FROM Note n WHERE n.userId = :user_id AND n.id = :note", Note.class)
				.setParameter("user_id", user).setParameter("note", noteId).getSingleResult();
	}

//	public Note updateExistingNote(String json) {
////		User user = userJpa.findByEmail("tarique@gmail.com");
//		JSONObject jsonObj = new JSONObject(json);
////		int noteId = jsonObj.getInt("id");
//		Note note = getSelectedNote(json);
//
//		int noteId = note.getId();
//		String title = jsonObj.getString("title");
//		String content = jsonObj.getString("content");
//
//		return null;
//	}

	public List<Note> getAllEmptyNotes() {
		User user = userJpa.findByEmail("tarique@gmail.com");
		List<Note> noteResult = entityManager
				.createQuery("Select n from Note n Where n.userId =:user_id And n.title=:title And n.content=:content ",
						Note.class)
				.setParameter("user_id", user).setParameter("title", "").setParameter("content", "").getResultList();

		return noteResult;
	}

	public List<Note> getUserBookmarks(String json) {
		User user = userJpa.findByEmail("tarique@gmail.com");
		List<Note> bookNotes = entityManager
				.createQuery("Select n from Note n Where n.userId =:user_id And n.bookmarked =true", Note.class)
				.setParameter("user_id", user).getResultList();
		return bookNotes;
	}
}

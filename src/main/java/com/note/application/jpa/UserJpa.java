package com.note.application.jpa;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.note.application.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Transactional
@Repository
public class UserJpa {

	@Autowired
	private EntityManager entityManager;

	public void save(User user) {
		entityManager.persist(user);
	}

	public void update(User user) {
		entityManager.merge(user);
	}

	public List<User> findAll() {
		return entityManager.createQuery("Select u from User u", User.class).getResultList();
	}

	public void delete(User user) {
		entityManager.remove(user);
	}

	public User findByEmail(String email) {
		List<User> list = entityManager.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
				.setParameter("email", email).getResultList();

		return list.isEmpty() ? null : list.get(0);
	}
}

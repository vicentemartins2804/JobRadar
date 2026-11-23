package com.jobrador.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.jobrador.backend.model.ApplicationStatus;
import com.jobrador.backend.model.JobApplication;

/**
 * Mock data source used until the Outlook sync + persistence layer exists.
 */
@Repository
public class InMemoryJobApplicationRepository implements JobApplicationRepository {

	private final List<JobApplication> applications = List.of(
			new JobApplication(1L, "Farfetch", "Frontend Developer", ApplicationStatus.INTERVIEW,
					LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 18), "Technical interview on 26 Nov"),
			new JobApplication(2L, "Talkdesk", "Java Backend Engineer", ApplicationStatus.APPLIED,
					LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10), "Wait for recruiter reply"),
			new JobApplication(3L, "OutSystems", "Full Stack Developer", ApplicationStatus.SCREENING,
					LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 15), "Complete HackerRank challenge"),
			new JobApplication(4L, "Feedzai", "Software Engineer", ApplicationStatus.REJECTED,
					LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 5), null),
			new JobApplication(5L, "Sword Health", "Junior Software Engineer", ApplicationStatus.OFFER,
					LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 20), "Reply to offer by 30 Nov"));

	@Override
	public List<JobApplication> findAll() {
		return applications;
	}

	@Override
	public Optional<JobApplication> findById(Long id) {
		return applications.stream().filter(a -> a.id().equals(id)).findFirst();
	}

}

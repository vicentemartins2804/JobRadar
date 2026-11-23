package com.jobrador.backend.repository;

import java.util.List;
import java.util.Optional;

import com.jobrador.backend.model.JobApplication;

public interface JobApplicationRepository {

	List<JobApplication> findAll();

	Optional<JobApplication> findById(Long id);

}

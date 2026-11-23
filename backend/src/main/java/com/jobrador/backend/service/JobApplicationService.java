package com.jobrador.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jobrador.backend.model.JobApplication;
import com.jobrador.backend.repository.JobApplicationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

	private final JobApplicationRepository repository;

	public List<JobApplication> findAll() {
		return repository.findAll();
	}

}

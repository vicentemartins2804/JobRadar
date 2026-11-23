package com.jobrador.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobrador.backend.model.JobApplication;
import com.jobrador.backend.service.JobApplicationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {

	private final JobApplicationService service;

	@GetMapping
	public List<JobApplication> getAll() {
		return service.findAll();
	}

}

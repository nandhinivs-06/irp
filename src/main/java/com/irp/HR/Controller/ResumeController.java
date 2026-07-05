package com.irp.HR.Controller;

import com.irp.HR.Dto.ResumeUploadResponse;
import com.irp.HR.Model.Resume;
import com.irp.HR.Service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * Candidate uploads a resume (PDF/DOCX, max 10MB). Text is extracted
     * and sent to Ollama for skill extraction before the response comes
     * back, so this call may take a few seconds depending on your model.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @RequestParam UUID candidateId,
            @RequestParam("file") MultipartFile file
    ) {
        Resume resume = resumeService.uploadAndParse(candidateId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(resume));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeUploadResponse> getResume(@PathVariable UUID resumeId) {
        Resume resume = resumeService.getResumeOrThrow(resumeId);
        return ResponseEntity.ok(toResponse(resume));
    }

    @GetMapping
    public ResponseEntity<List<ResumeUploadResponse>> listByCandidate(@RequestParam UUID candidateId) {
        List<ResumeUploadResponse> resumes = resumeService.listByCandidate(candidateId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(resumes);
    }

    private ResumeUploadResponse toResponse(Resume resume) {
        return new ResumeUploadResponse(
                resume.getId(),
                resume.getFileName(),
                resume.getExtractedSkills(),
                resume.getYearsOfExperience(),
                resume.getUploadedAt()
        );
    }
}

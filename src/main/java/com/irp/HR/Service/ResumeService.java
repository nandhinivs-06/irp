package com.irp.HR.Service;

import com.irp.HR.Dto.ParsedResumeData;
import com.irp.HR.Exception.ResumeNotFoundException;
import com.irp.HR.Exception.ResumeParsingException;
import com.irp.HR.Model.Resume;
import com.irp.HR.Repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final OllamaService ollamaService;

    // Field injection for this one (Lombok's @RequiredArgsConstructor only
    // picks up `final` fields above, so @Value still works fine alongside it).
    @Value("${app.upload-dir:uploads/resumes}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB, per FR-04
    private static final String PDF_TYPE = "application/pdf";
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * candidateId is taken as a plain parameter for now - swap this for
     * the authenticated principal's id once your JWT -> UserPrincipal
     * wiring exposes the candidate's own id directly, so a candidate
     * can't upload a resume under someone else's id.
     */
    @Transactional
    public Resume uploadAndParse(UUID candidateId, MultipartFile file) {
        validateFile(file);

        String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path destination = saveToDisk(file, storedFileName);

        String rawText = extractText(file, destination);
        ParsedResumeData parsed = ollamaService.extractSkills(rawText);
        String skillsCsv = String.join(", ", parsed.skills());

        Resume resume = Resume.create(candidateId, file.getOriginalFilename(), destination.toString());
        resume.setExtractedSkills(skillsCsv);
        resume.setYearsOfExperience(parsed.yearsOfExperience());
        return resumeRepository.save(resume);
    }

    public Resume getResumeOrThrow(UUID resumeId) {
        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
    }

    public List<Resume> listByCandidate(UUID candidateId) {
        return resumeRepository.findByCandidateId(candidateId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResumeParsingException("Uploaded file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResumeParsingException("File exceeds the 10 MB limit.");
        }

        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        boolean isPdf = PDF_TYPE.equals(contentType) || fileName.endsWith(".pdf");
        boolean isDocx = DOCX_TYPE.equals(contentType) || fileName.endsWith(".docx");

        if (!isPdf && !isDocx) {
            throw new ResumeParsingException(
                    "Only PDF and DOCX files are accepted. Got content-type: " + contentType
            );
        }
    }

    private Path saveToDisk(MultipartFile file, String storedFileName) {
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            Path destination = dir.resolve(storedFileName);
            file.transferTo(destination);
            return destination;
        } catch (IOException e) {
            throw new ResumeParsingException("Could not save the uploaded file: " + e.getMessage());
        }
    }

    private String extractText(MultipartFile file, Path savedPath) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean isPdf = PDF_TYPE.equals(file.getContentType()) || fileName.endsWith(".pdf");

        try {
            if (isPdf) {
                try (PDDocument doc = Loader.loadPDF(savedPath.toFile())) {
                    return new PDFTextStripper().getText(doc);
                }
            } else {
                try (InputStream is = Files.newInputStream(savedPath);
                     XWPFDocument doc = new XWPFDocument(is)) {
                    return new XWPFWordExtractor(doc).getText();
                }
            }
        } catch (IOException e) {
            throw new ResumeParsingException("Could not read text from the uploaded file: " + e.getMessage());
        }
    }
}
package com.irp.HR.Dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the recruiter submits to create a job: pick/type a title, paste
 * the JD text into a textarea on the frontend.
 *
 * (Status transitions don't use this DTO — see JobController, the
 * PATCH /jobs/{id}/status endpoint takes the new status as a query param
 * instead, since there's no separate UpdateJobRequest in the file list.)
 */
public record JobRequest(

        @NotBlank(message = "Job title is required")
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @NotBlank(message = "Job description is required")
        @Size(min = 20, message = "Paste the full job description (min 20 characters)")
        String description
) {
}


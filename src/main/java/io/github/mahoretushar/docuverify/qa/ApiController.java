package io.github.mahoretushar.docuverify.qa;

import io.github.mahoretushar.docuverify.ingest.IngestionService;
import io.github.mahoretushar.docuverify.ingest.IngestionService.IngestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    public record AskRequest(@NotBlank @Size(max = 1000) String question,
                             @Min(1) @Max(20) Integer topK) {
    }

    private final IngestionService ingestion;
    private final AnswerService answers;

    public ApiController(IngestionService ingestion, AnswerService answers) {
        this.ingestion = ingestion;
        this.answers = answers;
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResult upload(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException {
        return ingestion.ingest(auth.getName(), file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/ask")
    public AnswerService.Answer ask(Authentication auth, @Valid @RequestBody AskRequest request) {
        return answers.ask(auth.getName(), request.question(), request.topK());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badInput(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}

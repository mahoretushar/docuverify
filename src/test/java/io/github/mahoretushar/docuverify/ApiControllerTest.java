package io.github.mahoretushar.docuverify;

import io.github.mahoretushar.docuverify.ingest.IngestionService;
import io.github.mahoretushar.docuverify.qa.AnswerService;
import io.github.mahoretushar.docuverify.qa.ApiController;
import io.github.mahoretushar.docuverify.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
class ApiControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean IngestionService ingestion;
    @MockitoBean AnswerService answers;

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ask").with(httpBasic("asha", "asha-pass"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topKAboveLimitIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ask").with(httpBasic("asha", "asha-pass")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the deadline?\",\"topK\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadIsStoredUnderTheCallersName() throws Exception {
        when(ingestion.ingest(eq("asha"), eq("policy.md"), any()))
                .thenReturn(new IngestionService.IngestResult("doc-1", "policy.md", 4));
        mvc.perform(multipart("/api/v1/documents").with(httpBasic("asha", "asha-pass"))
                        .file(new MockMultipartFile("file", "policy.md", "text/markdown", "# Policy".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunks").value(4));
    }
}

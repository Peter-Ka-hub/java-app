package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class FrontendWebTests extends ApiTestSupport {
  @Test
  void anonymousVisitorsCanOpenTheLoginPageWithoutAnAuthenticationRedirect() throws Exception {
    mvc.perform(get("/").with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("index.html"));
    var result =
        mvc.perform(get("/index.html").with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn();
    assertThat(result.getResponse().getHeader("Content-Security-Policy"))
        .contains("default-src 'self'", "frame-ancestors 'self'");
    assertThat(result.getResponse().getContentAsString()).isNotBlank();
  }

  @Test
  void anonymousVisitorsCanLoadEveryFrontendRuntimeAsset() throws Exception {
    mvc.perform(get("/ui/styles.css").with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/css"));
    for (String path : new String[] {"/ui/api.js", "/ui/model.js", "/ui/app.js"}) {
      var response =
          mvc.perform(get(path).with(anonymous()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse();
      assertThat(response.getContentType()).contains("javascript");
      assertThat(response.getContentAsString()).isNotBlank();
    }
  }

  @Test
  void publicFrontendDoesNotExposePrivateApiCollections() throws Exception {
    for (String path :
        new String[] {
          "/api/schools",
          "/api/users",
          "/api/academic-years",
          "/api/classes",
          "/api/subjects",
          "/api/enrollments",
          "/api/grades",
          "/api/auth/me"
        }) mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
  }

  @Test
  void publicFrontendDoesNotBypassCsrfOrGrantAnAnonymousWriterAccess() throws Exception {
    String body = objectMapper.writeValueAsString(schoolRequest());
    mvc.perform(
            post("/api/schools")
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/schools")
                .with(anonymous())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }
}

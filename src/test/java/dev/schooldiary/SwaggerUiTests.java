package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class SwaggerUiTests extends ApiTestSupport {
  @Test
  void anonymousSwaggerInitializerInstallsTheAutomaticCsrfInterceptor() throws Exception {
    String script =
        mvc.perform(get("/swagger-ui/swagger-initializer.js").with(anonymous()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(script)
        .contains("requestInterceptor: window.schoolDiaryCsrfInterceptor")
        .contains("root.schoolDiaryCsrfInterceptor =")
        .contains("api/auth/csrf")
        .contains("credentials: \"same-origin\"")
        .contains("/v3/api-docs/swagger-config");
    assertThat(script.indexOf("root.schoolDiaryCsrfInterceptor ="))
        .isLessThan(script.indexOf("window.ui = SwaggerUIBundle({"));
  }

  @Test
  void swaggerHtmlAndItsRuntimeAssetsStayPublic() throws Exception {
    for (String path : new String[] {"/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js"})
      mvc.perform(get(path).with(anonymous())).andExpect(status().isOk());
  }
}

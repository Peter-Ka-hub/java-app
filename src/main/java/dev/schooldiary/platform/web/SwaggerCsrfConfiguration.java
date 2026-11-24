package dev.schooldiary.platform.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.resource.TransformedResource;

/** Adds session-aware CSRF handling through Swagger UI's request interceptor extension point. */
@Configuration(proxyBeanMethods = false)
public class SwaggerCsrfConfiguration {
  @Bean
  SwaggerIndexTransformer csrfSwaggerIndexTransformer(
      SwaggerUiConfigProperties config,
      SwaggerUiOAuthProperties oauth,
      SwaggerWelcomeCommon welcome,
      ObjectMapperProvider mapper)
      throws IOException {
    String script =
        new ClassPathResource("static/js/swagger-csrf.js")
            .getContentAsString(StandardCharsets.UTF_8);
    var standard = new SwaggerIndexPageTransformer(config, oauth, welcome, mapper);
    return (request, resource, chain) -> {
      var transformed = standard.transform(request, resource, chain);
      if (!"swagger-initializer.js".equals(resource.getFilename())) return transformed;
      String initializer = transformed.getContentAsString(StandardCharsets.UTF_8);
      String marker = "window.ui = SwaggerUIBundle({";
      if (!initializer.contains(marker))
        throw new IOException(
            "Swagger UI initializer no longer exposes the expected configuration");
      initializer =
          initializer.replace(
              marker, marker + "\n    requestInterceptor: window.schoolDiaryCsrfInterceptor,");
      return new TransformedResource(
          transformed, (script + "\n" + initializer).getBytes(StandardCharsets.UTF_8));
    };
  }
}

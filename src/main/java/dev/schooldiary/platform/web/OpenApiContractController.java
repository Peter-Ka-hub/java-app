package dev.schooldiary.platform.web;

import java.io.IOException;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the conventional tooling URLs from the same contract used to generate the API. */
@RestController
public class OpenApiContractController {
  private final byte[] json;
  private final byte[] yaml;

  public OpenApiContractController(
      @Value("classpath:openapi/openapi.json") Resource contract, ObjectMapperProvider mapper)
      throws IOException {
    json = contract.getContentAsByteArray();
    yaml = mapper.yamlMapper().writeValueAsBytes(mapper.jsonMapper().readTree(json));
  }

  @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public byte[] json() {
    return json;
  }

  @GetMapping(value = "/v3/api-docs.yaml", produces = Constants.APPLICATION_OPENAPI_YAML)
  public byte[] yaml() {
    return yaml;
  }
}

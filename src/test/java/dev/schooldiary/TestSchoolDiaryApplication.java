package dev.schooldiary;

import org.springframework.boot.SpringApplication;

public class TestSchoolDiaryApplication {

  public static void main(String[] args) {
    SpringApplication application =
        new SpringApplication(SchoolDiaryApplication.class, TestcontainersConfiguration.class);
    application.setAdditionalProfiles("test");
    application.run(args);
  }
}

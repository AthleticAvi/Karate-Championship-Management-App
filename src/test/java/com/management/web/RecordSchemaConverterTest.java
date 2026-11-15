package com.management.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RecordSchemaConverterTest {

  private record Named(@NotBlank String name) {}

  /**
   * Every character on its own, against the real validator: one the pattern finds is one that
   * passes {@code @NotBlank}, and the other way round. Longer text passes both when any of its
   * characters does.
   */
  @Test
  void notBlankPattern_takesExactlyWhatNotBlankTakes() {
    Pattern notBlank = Pattern.compile(RecordSchemaConverter.NOT_BLANK);
    List<String> differ = new ArrayList<>();
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      for (int code = Character.MIN_VALUE; code <= Character.MAX_VALUE; code++) {
        String text = String.valueOf((char) code);
        boolean described = notBlank.matcher(text).find();
        boolean valid = validator.validate(new Named(text)).isEmpty();
        if (described != valid) {
          differ.add(String.format("U+%04X", code));
        }
      }
    }

    assertThat(differ).isEmpty();
    assertThat(notBlank.matcher(" \t").find()).isFalse();
    assertThat(notBlank.matcher(" a ").find()).isTrue();
  }
}

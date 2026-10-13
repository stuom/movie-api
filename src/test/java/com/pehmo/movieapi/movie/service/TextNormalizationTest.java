package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextNormalizationTest {

  @Nested
  class Normalize {

    @Test
    void shouldRemoveLeadingAndTrailingSpacesWhenNormalizing() {
      // given
      String text = "  Aliens  ";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("aliens");
    }

    @Test
    void shouldCollapseRepeatedSpacesWhenNormalizing() {
      // given
      String text = "the   lord    of the rings";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("the lord of the rings");
    }

    @Test
    void shouldCollapseTabsAndNewlinesToOneSpaceWhenNormalizing() {
      // given
      String text = "star \t\n wars\r\n\tnew hope";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("star wars new hope");
    }

    @Test
    void shouldTreatEmSpaceAsWhitespaceWhenItIsInsideTheText() {
      // given
      String text = "star\u2003\u2003wars";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("star wars");
    }

    @Test
    void shouldStripEmSpaceAtBothEndsWhenNormalizing() {
      // given
      String text = "\u2003Aliens\u2003";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("aliens");
    }

    @Test
    void shouldKeepNonBreakingSpaceWhenNormalizing() {
      // given
      String text = "star\u00A0wars";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("star\u00A0wars");
    }

    @Test
    void shouldLowercaseWhenNormalizing() {
      // given
      String text = "ALIENS Returns";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("aliens returns");
    }

    @Test
    void shouldGiveSameResultWhenAccentIsComposedOrDecomposed() {
      // given
      String composed = "Am\u00E9lie";
      String decomposed = "Ame\u0301lie";

      // when
      String composedResult = TextNormalization.normalize(composed);
      String decomposedResult = TextNormalization.normalize(decomposed);

      // then
      assertThat(decomposedResult).isEqualTo(composedResult);
    }

    @Test
    void shouldKeepAccentsWhenNormalizing() {
      // given
      String text = "Am\u00C9lie";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEqualTo("am\u00E9lie");
    }

    @Test
    void shouldLowercaseWithRootLocaleWhenDefaultLocaleIsTurkish() {
      // given
      Locale originalLocale = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr"));
      try {
        // when
        String result = TextNormalization.normalize("TITLE");

        // then
        assertThat(result).isEqualTo("title");
      } finally {
        Locale.setDefault(originalLocale);
      }
    }

    @Test
    void shouldReturnEmptyStringWhenTextIsBlank() {
      // given
      String text = " \t\n\u2003 ";

      // when
      String result = TextNormalization.normalize(text);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowNullPointerExceptionWhenNormalizingNull() {
      // given
      String text = null;

      // when / then
      assertThatNullPointerException().isThrownBy(() -> TextNormalization.normalize(text));
    }
  }

  @Nested
  class Trim {

    @Test
    void shouldRemoveLeadingAndTrailingWhitespaceWhenTrimming() {
      // given
      String text = " \t Downey Jr.\u2003\n";

      // when
      String result = TextNormalization.trim(text);

      // then
      assertThat(result).isEqualTo("Downey Jr.");
    }

    @Test
    void shouldKeepCaseAndInnerSpacingWhenTrimming() {
      // given
      String text = "  The   LORD  ";

      // when
      String result = TextNormalization.trim(text);

      // then
      assertThat(result).isEqualTo("The   LORD");
    }

    @Test
    void shouldKeepDecomposedFormWhenTrimming() {
      // given
      String decomposed = "Ame\u0301lie";

      // when
      String result = TextNormalization.trim(decomposed);

      // then
      assertThat(result).isEqualTo(decomposed);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTrimmingNull() {
      // given
      String text = null;

      // when / then
      assertThatNullPointerException().isThrownBy(() -> TextNormalization.trim(text));
    }
  }
}

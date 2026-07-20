package com.campkin.service;
import org.junit.jupiter.api.Test; import java.util.*; import static org.assertj.core.api.Assertions.assertThat;
class NameMatcherTest { @Test void normalizesPunctuationAndCase(){assertThat(NameMatcher.normalize("  PÉTER   G. ")).isEqualTo("peter g");} @Test void splitsEverySupportedSeparator(){assertThat(NameMatcher.split("John, Peter;Mark / Luke - Sam\nAlex")).containsExactly("John","Peter","Mark","Luke","Sam","Alex");} }

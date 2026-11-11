package com.management.kumitegame.rules;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At time up with nothing left to separate the sides: the format decides, HANTEI or HIKIWAKE (WKF
 * Kumite Competition Rules 2026.01, Articles 12.2.4, 12.2.5, Appendix 6). The one step that differs
 * by format, so it asks the format's {@link FormatRules}.
 */
@Component
@Order(80)
public class LevelRule implements SuggestionRule {

  private final Map<GameFormat, FormatRules> formats;

  /**
   * Refuses to start without exactly one {@link FormatRules} per format.
   *
   * @throws IllegalStateException when a format has none, or two
   */
  public LevelRule(List<FormatRules> formats) {
    this.formats =
        formats.stream()
            .collect(Collectors.toUnmodifiableMap(FormatRules::format, Function.identity()));
    if (!this.formats.keySet().containsAll(EnumSet.allOf(GameFormat.class))) {
      throw new IllegalStateException(
          "Every format needs its rules, but only " + this.formats.keySet() + " have them.");
    }
  }

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    if (!standing.timeUp()) {
      return Optional.empty();
    }
    return Optional.of(Objects.requireNonNull(formats.get(standing.format())).whenLevel(standing));
  }
}

package com.management.kumitegame.rules;

import com.management.kumitegame.domain.ResultRules;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The suggested-result chain: asks each {@link SuggestionRule} in order, and the first that answers
 * decides.
 *
 * <p>The list is injected: the container gives every rule it finds, sorted by {@code @Order}, so
 * adding a rule is adding a class. It holds no state and writes nothing: the game asks it whenever
 * it is folded. See {@code src/main/java/com/management/kumitegame/rules/README.md}.
 */
@Component
public class SuggestedResultChain implements ResultRules {

  private final List<SuggestionRule> rules;

  public SuggestedResultChain(List<SuggestionRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    return rules.stream().map(rule -> rule.suggest(standing)).flatMap(Optional::stream).findFirst();
  }
}

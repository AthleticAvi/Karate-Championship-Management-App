package com.management.kumitegame.domain;

/** The prohibited behaviour a penalty was given for, in the order of Article 9.1.1. Optional. */
public enum Infraction {
  EXCESSIVE_CONTACT,
  ATTACK_TO_PROHIBITED_AREA,
  OPEN_HAND_TO_FACE,
  TECHNIQUE_AFTER_WAKARETE,
  DANGEROUS_THROW,
  FEIGNING_INJURY,
  JOGAI,
  MUBOBI,
  AVOIDING_COMBAT,
  PASSIVITY,
  CLINCHING,
  GRABBING_WITH_BOTH_HANDS,
  GRABBING_WITH_ONE_HAND,
  UNCONTROLLED_TECHNIQUE,
  HEAD_KNEE_OR_ELBOW_ATTACK,
  KICKING_DOWNED_OPPONENT,
  DISCOURTESY;

  /**
   * Avoiding combat as Article 12.2.8 lists it: JOGAI, running away, clinching, grabbing,
   * wrestling, pushing or standing chest to chest. Running away is {@link #AVOIDING_COMBAT}, and
   * wrestling, pushing and standing chest to chest are {@link #CLINCHING}, as Article 9.1.1 groups
   * them. Not {@link #PASSIVITY}, which cannot be given in the final 15 seconds.
   */
  public boolean isAvoidingCombat() {
    return this == JOGAI
        || this == AVOIDING_COMBAT
        || this == CLINCHING
        || this == GRABBING_WITH_BOTH_HANDS
        || this == GRABBING_WITH_ONE_HAND;
  }
}

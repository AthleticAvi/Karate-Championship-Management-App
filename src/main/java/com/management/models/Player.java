package com.management.models;

import com.management.enums.PointsType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Player {
  @Id private String id;

  /**
   * Detects concurrent modification: two simultaneous point awards both read the same score, and
   * without this field the second write silently discarded the first.
   *
   * <p>Primitive {@code long}, so an unset version reads as {@code 0} — which Spring Data treats as
   * <em>new</em>. A document written before this field existed therefore cannot be updated: its
   * next save is attempted as an insert and fails on the duplicate identifier. That is accepted
   * rather than backfilled, because there is no production data; see {@code CLAUDE.md}.
   */
  @Version private long version;

  private String name;
  private Points points;
  private Foul fouls;

  public Player() {}

  public Player(String name) {
    this.name = name;
    this.points = new Points();
    this.fouls = new Foul();
  }

  public Player(String id, String name, Points points, Foul fouls) {
    this.id = id;
    this.name = name;
    this.points = points;
    this.fouls = fouls;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Points getPoints() {
    return points;
  }

  public void setPoints(Points points) {
    this.points = points;
  }

  public Foul getFouls() {
    return this.fouls;
  }

  public void setFouls(Foul fouls) {
    this.fouls = fouls;
  }

  public void addPoint(PointsType pointType) {
    pointType.getStrategy().addPoint(this.points);
  }

  public void removePoint(PointsType pointType) {
    pointType.getStrategy().removePoint(this.points);
  }

  public void addFoul() {
    this.fouls.addFoul();
  }

  public void removeFoul() {
    this.fouls.removeFoul();
  }

  public long getVersion() {
    return version;
  }

  /**
   * Identity equality on the persistent id (#60): two objects with the same id are the same stored
   * fighter, even if one is stale. An unsaved fighter has no id and is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Player otherPlayer)) {
      return false;
    }
    return id != null && id.equals(otherPlayer.id);
  }

  /**
   * Constant, deliberately.
   *
   * <p>Hashing the identifier looks better distributed but breaks the contract: {@code save}
   * assigns the id, so a fighter put in a {@code HashSet} before saving lands in one bucket and is
   * looked for in another afterwards — silently unreachable. A constant keeps the hash stable
   * across that transition, which is the property collections actually require; equality still
   * separates the instances.
   */
  @Override
  public int hashCode() {
    return Player.class.hashCode();
  }

  @Override
  public String toString() {
    return "Player{id=" + id + ", name=" + name + ", points=" + points + ", fouls=" + fouls + "}";
  }
}

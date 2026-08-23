package com.management.models;

public class Points {
  private int numOfPoints;

  public Points() {
    this.numOfPoints = 0;
  }

  public int getNumOfPoints() {
    return numOfPoints;
  }

  public void setNumOfPoints(int numOfPoints) {
    this.numOfPoints = numOfPoints;
  }

  /** Value equality (#60): a score object is nothing but its count. */
  @Override
  public boolean equals(Object other) {
    return other instanceof Points otherPoints && numOfPoints == otherPoints.numOfPoints;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(numOfPoints);
  }

  @Override
  public String toString() {
    return "Points{" + numOfPoints + "}";
  }
}

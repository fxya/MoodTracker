package com.example.moodtracker.model;

/**
 * View-model for the "this week vs. last week" stat card on the mood tracker page. Not a JPA entity
 * - computed on demand from a bounded mood lookup.
 */
public record WeeklySummary(
    int entryCount, Double averageThisWeek, Double averageLastWeek, String topTag) {

  public boolean hasData() {
    return entryCount > 0;
  }

  public boolean hasComparison() {
    return averageThisWeek != null && averageLastWeek != null;
  }

  public double delta() {
    return hasComparison() ? averageThisWeek - averageLastWeek : 0;
  }
}

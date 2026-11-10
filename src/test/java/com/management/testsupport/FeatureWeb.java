package com.management.testsupport;

import java.util.regex.Pattern;

/**
 * Where the application's controllers and request and response records live: one {@code web}
 * package per feature. The shared {@code com.management.web} holds neither.
 *
 * <p>Tests that read the endpoints or the API shapes from the code select them with this, so a
 * feature added later is covered without changing them.
 */
public final class FeatureWeb {

  private static final Pattern PACKAGE = Pattern.compile("com\\.management\\.\\w+\\.web");

  private FeatureWeb() {}

  public static boolean holds(String packageName) {
    return PACKAGE.matcher(packageName).matches();
  }
}

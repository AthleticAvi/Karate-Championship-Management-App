package com.management.testsupport;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The one web-slice configuration, stated once so every controller slice shares a cached context.
 *
 * <p><strong>Why a base class.</strong> The framework caches a context per distinct configuration,
 * and the key includes the annotation set and the set of mocked bean types. Two slice classes that
 * each declared their own configuration would be two context builds — the thing that comes to
 * dominate suite time as the number of slice classes grows. Subclasses declare identical mocked
 * beans for the same reason.
 *
 * <p><strong>No configuration anchor of our own.</strong> {@code @WebMvcTest} walks up the package
 * tree and finds {@code com.management.KumiteGameStarter} — since #46 put it at the root, the
 * search finds the real application class, and the slice's own filters keep the context to the web
 * layer: controllers and advice start, services and repositories do not. The empty {@code
 * SliceTestConfiguration} that used to stand in for it is gone.
 *
 * <p>Only the web layer starts: no database, no service logic. That is what makes these tests cost
 * milliseconds rather than seconds, and it is why they are the right place for routing, status
 * codes and serialisation and the wrong place for anything else.
 *
 * <p><strong>Active profile: test.</strong> Stated here rather than per subclass, so the slice
 * still resolves to one cached context. It matches {@code IntegrationTestBase}: every test in the
 * suite runs under {@code test}, and none under the {@code dev} default meant for a developer's
 * machine.
 */
@WebMvcTest
@ActiveProfiles("test")
public abstract class WebSliceTestBase {}

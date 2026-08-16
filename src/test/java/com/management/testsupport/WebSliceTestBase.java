package com.management.testsupport;

import com.management.controllers.KumiteGameController;
import com.management.controllers.PlayerController;
import com.management.exceptions.GlobalExceptionHandler;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

/**
 * The one web-slice configuration, stated once so every controller slice shares a cached context.
 *
 * <p><strong>Why a base class.</strong> The framework caches a context per distinct configuration,
 * and the key includes the imports and the set of mocked bean types. Two slice classes that each
 * name their own controller would be two configurations and two context builds — the thing that
 * comes to dominate suite time as the number of slice classes grows. Both controllers are imported
 * here, so every subclass resolves to the same key.
 *
 * <p><strong>Why the controllers are imported rather than selected.</strong>
 * {@code @WebMvcTest(controllers = ...)} filters a component scan, and the configuration anchor
 * these tests resolve to declares no scan at all — see {@code
 * com.management.SliceTestConfiguration}. Without an import the controller bean is never created
 * and every request falls to the static-resource handler, producing a confusing 500 rather than an
 * obvious wiring failure.
 *
 * <p><strong>Mocked collaborators stay in the subclasses.</strong> Declaring them here would mean
 * {@code protected} fields, and a subclass can express what it stubs more clearly than an inherited
 * field can. Both services must be mocked in every subclass regardless of what it exercises: {@link
 * KumiteGameController} injects both, so the context will not start otherwise — and identical mock
 * declarations are what keep the cache key identical.
 *
 * <p>Only the web layer starts: no database, no service logic, no component scan. That is what
 * makes these tests cost milliseconds rather than seconds, and it is why they are the right place
 * for routing, status codes and serialisation and the wrong place for anything else.
 */
@WebMvcTest
@Import({KumiteGameController.class, PlayerController.class, GlobalExceptionHandler.class})
public abstract class WebSliceTestBase {}

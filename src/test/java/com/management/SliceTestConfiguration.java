package com.management;

import org.springframework.boot.SpringBootConfiguration;

/**
 * The configuration anchor that test slices search for. Registers nothing.
 *
 * <p><strong>Why this is needed.</strong> A slice annotation such as {@code @WebMvcTest} locates
 * the application configuration by walking <em>up</em> the package tree from the test class. Tests
 * live in {@code com.management.kumitegametests} and the application class is {@code
 * com.management.kumitegame.KumiteGameStarter} — a sibling package, not a parent — so the search
 * fails with "Unable to find a @SpringBootConfiguration". This class sits at {@code
 * com.management}, a genuine parent of every test package, so the search succeeds.
 *
 * <p><strong>Why not point the slice at the real application class instead.</strong> Naming {@code
 * KumiteGameStarter} in {@code @ContextConfiguration} drags in its component scan, which starts the
 * services and their repositories and then fails looking for a MongoDB connection. That is the
 * opposite of a slice: the point is to start one layer, not all of them.
 *
 * <p>Deliberately empty. It declares no beans, so a slice test gets exactly what its slice
 * annotation asks for and nothing else. Integration tests are unaffected — they name {@code
 * KumiteGameStarter} explicitly and so never consult this class.
 *
 * <p>Delete this once #46 moves the application class to {@code com.management}, at which point the
 * upward search finds the real one.
 */
@SpringBootConfiguration
public class SliceTestConfiguration {}

package com.management.kumitegametests;

import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.services.KumiteGameService;
import com.management.util.GameConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KumiteGameTimerTest {
    @Mock
    private KumiteGameRepository kumiteGameRepository;

    @Mock
    private GameConfig gameConfig;

    @InjectMocks
    private KumiteGameService kumiteGameService;

    private KumiteGame testGame;

    private AutoCloseable mocks = MockitoAnnotations.openMocks(this);

    @BeforeEach
    public void setUp() {

        mocks = MockitoAnnotations.openMocks(this);

        Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);
        playersMap.put(PlayerColor.RED, new Player("Player 1"));
        playersMap.put(PlayerColor.BLUE, new Player("Player 2"));
        List<Referee> refereesList = Collections.singletonList(new Referee("Referee 1"));

        when(gameConfig.getDefaultDuration()).thenReturn(Duration.ofSeconds(120));
        testGame = new KumiteGame(playersMap, refereesList, gameConfig.getDefaultDuration());

        when(kumiteGameRepository.save(any(KumiteGame.class))).thenAnswer(invocation -> invocation.getArgument(0));

        testGame = kumiteGameRepository.save(testGame);
        when(kumiteGameRepository.findById(testGame.getId())).thenReturn(Optional.of(testGame));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testStartGame() {
        testGame = kumiteGameService.startGame(testGame.getId());

        assertNotNull(testGame.getStartTime());
        assertEquals(Duration.ofSeconds(120), testGame.getRemainingTime());
        assertEquals(GameState.RUNNING, testGame.getGameState());
    }

    @Test
    void testPauseGame() {
        testGame = kumiteGameService.startGame(testGame.getId());
        testGame = kumiteGameService.pauseGame(testGame.getId());

        assertEquals(GameState.PAUSED, testGame.getGameState());
        assertNull(testGame.getStartTime());
    }

    @Test
    void testResumeGame() {
        testGame = kumiteGameService.startGame(testGame.getId());
        testGame = kumiteGameService.pauseGame(testGame.getId());
        testGame = kumiteGameService.resumeGame(testGame.getId());

        assertEquals(GameState.RUNNING, testGame.getGameState());
        assertNotNull(testGame.getStartTime());
    }

    @Test
    void testEndGame() {
        testGame = kumiteGameService.startGame(testGame.getId());
        testGame = kumiteGameService.endGame(testGame.getId());

        assertEquals(GameState.FINISHED, testGame.getGameState());
        assertEquals(Duration.ZERO, testGame.getRemainingTime());
    }

    @Test
    void testResumeGameFromPaused() {
        testGame = kumiteGameService.startGame(testGame.getId());
        testGame = kumiteGameService.pauseGame(testGame.getId());

        Duration remainingBeforeResume = testGame.getRemainingTime();

        testGame = kumiteGameService.resumeGame(testGame.getId());

        assertEquals(GameState.RUNNING, testGame.getGameState());
        assertNotNull(testGame.getStartTime());
        assertEquals(remainingBeforeResume, testGame.getRemainingTime());
    }

}
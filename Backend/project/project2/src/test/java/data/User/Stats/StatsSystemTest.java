package data.User.Stats;

import data.User.AppUser;
import data.User.AppUserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatsSystemTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUser testUser;
    private Long createdUserId;
    private Long createdStatsId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        // Create test user only
        testUser = new AppUser();
        testUser.setUsername("testuser_" + System.currentTimeMillis());
        testUser.setEmail("test_" + System.currentTimeMillis() + "@example.com");
        testUser = appUserRepository.save(testUser);
        createdUserId = testUser.getUserID();
    }

    @AfterEach
    void tearDown() {
        // Only delete the test data we created
        try {
            if (createdStatsId != null) {
                userStatsRepository.deleteById(createdStatsId);
            }
            if (createdUserId != null) {
                UserStats stats = userStatsRepository.findByAppUserId(createdUserId);
                if (stats != null) {
                    userStatsRepository.delete(stats);
                }
                appUserRepository.deleteById(createdUserId);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testGetAllUserStats() {
        // Create stats for test user
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetUserStatsById() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("allGameStats", notNullValue())
                .body("allGameStats.GoFish", notNullValue())
                .body("allGameStats.Blackjack", notNullValue())
                .body("allGameStats.Euchre", notNullValue());
    }

    @Test
    void testGetUserStatsById_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", 99999L)
                .then()
                .statusCode(500);
    }

    @Test
    void testCreateUserStats() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("allGameStats.GoFish", notNullValue())
                .body("allGameStats.Blackjack", notNullValue())
                .body("allGameStats.Euchre", notNullValue());

        // Verify stats were created in database
        UserStats stats = userStatsRepository.findByAppUserId(testUser.getUserID());
        assertNotNull(stats);
        assertNotNull(stats.getGameStats("GoFish"));
        assertNotNull(stats.getGameStats("Blackjack"));
        assertNotNull(stats.getGameStats("Euchre"));
    }

    @Test
    void testCreateUserStats_UserNotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/UserStats/{id}", 99999L)
                .then()
                .statusCode(500);
    }

    @Test
    void testDeleteUserStats() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body(equalTo("{\"message\":\"success\"}"));

        // Verify stats were deleted
        UserStats stats = userStatsRepository.findByAppUserId(testUser.getUserID());
        assertNull(stats);
    }

    @Test
    void testDeleteUserStats_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/UserStats/{id}", 99999L)
                .then()
                .statusCode(200)
                .body(equalTo("{\"message\":\"failure\"}"));
    }

    @Test
    void testDeleteUserStatsByUserStatID() {
        Long statsId = createUserStatsAndgetUserID(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/UserStats/statID/{id}", statsId)
                .then()
                .statusCode(200)
                .body(equalTo("{\"message\":\"success\"}"));
    }

    @Test
    void testIncrementGoFishStat_GoneFishing() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "GoFish", "goneFishing")
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.timesWentFishing", equalTo(1));
    }

    @Test
    void testIncrementGoFishStat_QuestionAsked() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "GoFish", "questionAsked")
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.questionsAsked", equalTo(1));
    }

    @Test
    void testIncrementEuchreStat_TrickTaken() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "Euchre", "trickTaken")
                .then()
                .statusCode(200)
                .body("allGameStats.Euchre.tricksTaken", equalTo(1));
    }

    @Test
    void testIncrementBlackjackStat_BetWon() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "Blackjack", "betWon")
                .then()
                .statusCode(200)
                .body("allGameStats.Blackjack.betsWon", equalTo(1));
    }

    @Test
    void testIncrementStat_InvalidGame() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "InvalidGame", "someStat")
                .then()
                .statusCode(500);
    }

    @Test
    void testIncrementStat_InvalidStat() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "GoFish", "invalidStat")
                .then()
                .statusCode(500);
    }

    @Test
    void testSetGoFishStat() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/set/{stat}/{amount}",
                        testUser.getUserID(), "GoFish", "goneFishing", 10)
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.timesWentFishing", equalTo(10));
    }

    @Test
    void testSetEuchreStat() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/set/{stat}/{amount}",
                        testUser.getUserID(), "Euchre", "trickTaken", 25)
                .then()
                .statusCode(200)
                .body("allGameStats.Euchre.tricksTaken", equalTo(25));
    }

    @Test
    void testSetBlackjackStat() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/set/{stat}/{amount}",
                        testUser.getUserID(), "Blackjack", "betWon", 50)
                .then()
                .statusCode(200)
                .body("allGameStats.Blackjack.betsWon", equalTo(50));
    }

    @Test
    void testSetStat_InvalidStat() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/set/{stat}/{amount}",
                        testUser.getUserID(), "GoFish", "invalidStat", 10)
                .then()
                .statusCode(500);
    }

    @Test
    void testIncrementMultipleStats() {
        createUserStats(testUser.getUserID());

        // Increment GoFish stats multiple times
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .when()
                    .put("/UserStats/{id}/{gameName}/increment/{stat}",
                            testUser.getUserID(), "GoFish", "goneFishing")
                    .then()
                    .statusCode(200);
        }

        // Verify final count
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.timesWentFishing", equalTo(5));
    }

    @Test
    void testIncrementGamePlayed() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/increment/{stat}",
                        testUser.getUserID(), "GoFish", "gamePlayed")
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.gamesPlayed", equalTo(1));
    }

    @Test
    void testSetGamePlayed() {
        createUserStats(testUser.getUserID());

        given()
                .contentType(ContentType.JSON)
                .when()
                .put("/UserStats/{id}/{gameName}/set/{stat}/{amount}",
                        testUser.getUserID(), "Blackjack", "gamePlayed", 100)
                .then()
                .statusCode(200)
                .body("allGameStats.Blackjack.gamesPlayed", equalTo(100));
    }

    @Test
    void testAllGoFishStats() {
        createUserStats(testUser.getUserID());

        // Increment all GoFish stats
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "GoFish", "goneFishing");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "GoFish", "questionAsked");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "GoFish", "bookCollected");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "GoFish", "gameWon");

        // Verify all stats
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("allGameStats.GoFish.timesWentFishing", equalTo(1))
                .body("allGameStats.GoFish.questionsAsked", equalTo(1))
                .body("allGameStats.GoFish.booksCollected", equalTo(1))
                .body("allGameStats.GoFish.gamesWon", equalTo(1));
    }

    @Test
    void testAllEuchreStats() {
        createUserStats(testUser.getUserID());

        // Increment all Euchre stats
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Euchre", "trickTaken");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Euchre", "pickedUp");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Euchre", "goneAlone");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Euchre", "sweepWon");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Euchre", "gameWon");

        // Verify all stats
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("allGameStats.Euchre.tricksTaken", equalTo(1))
                .body("allGameStats.Euchre.timesPickedUp", equalTo(1))
                .body("allGameStats.Euchre.timesGoneAlone", equalTo(1))
                .body("allGameStats.Euchre.sweepsWon", equalTo(1))
                .body("allGameStats.Euchre.gamesWon", equalTo(1));
    }

    @Test
    void testAllBlackjackStats() {
        createUserStats(testUser.getUserID());

        // Increment all Blackjack stats
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Blackjack", "betWon");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Blackjack", "doubledDown");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Blackjack", "split");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Blackjack", "hit");
        given().put("/UserStats/{id}/{gameName}/increment/{stat}",
                testUser.getUserID(), "Blackjack", "gameWon");

        // Verify all stats
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/UserStats/{id}", testUser.getUserID())
                .then()
                .statusCode(200)
                .body("allGameStats.Blackjack.betsWon", equalTo(1))
                .body("allGameStats.Blackjack.timesDoubledDown", equalTo(1))
                .body("allGameStats.Blackjack.timesSplit", equalTo(1))
                .body("allGameStats.Blackjack.timesHit", equalTo(1))
                .body("allGameStats.Blackjack.gamesWon", equalTo(1));
    }

    // Helper method to create user stats and track ID
    private Long createUserStatsAndgetUserID(long userId) {
        String response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/UserStats/{id}", userId)
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Extract the stats ID from response using JsonPath
        Long statsId = io.restassured.path.json.JsonPath.from(response).getLong("id");
        createdStatsId = statsId;
        return statsId;
    }

    // Helper method to create user stats without deserialization
    private void createUserStats(long userId) {
        createUserStatsAndgetUserID(userId);
    }
}
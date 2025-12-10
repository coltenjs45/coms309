package data.User;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JakeSystemTest {

    @LocalServerPort
    private int port;

    private long createdUserId;

    @BeforeAll
    public void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.registerParser("text/plain", Parser.JSON);
    }

    @Test
    @Order(1)
    public void testCreateUser() {
        String body = """
    {
      "username": "jake_test",
      "password": "pass123",
      "email": "jake@example.com",
      "firstName": "Jake",
      "lastName": "Breyfogle",
      "age": 20
    }
    """;

        // Create the user
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/AppUser")
                .then()
                .statusCode(200)
                .body("message", equalTo("success"));

        // Pull ID by username for later tests
        createdUserId =
                ((Integer)
                        given()
                                .pathParam("username", "jake_test")
                                .when()
                                .get("/AppUser/username/{username}")
                                .then()
                                .statusCode(200)
                                .body("username", equalTo("jake_test"))
                                .extract()
                                .path("userID"))
                        .longValue();
    }

    // -------------------------------------------
    // 2. DUPLICATE USERNAME FAILS
    // -------------------------------------------
    @Test
    @Order(2)
    public void testDuplicateUsernameFails() {
        String body = """
        {
          "username": "jake_test",
          "password": "anotherPass",
          "email": "duplicate@example.com",
          "firstName": "Dup",
          "lastName": "User",
          "age": 50
        }
        """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/AppUser")
                .then()
                .statusCode(409)
                .body(equalTo("Username already exists"));
    }

    // -------------------------------------------
    // 3. UPDATE USER BY ID
    // -------------------------------------------
    @Test
    @Order(3)
    public void testUpdateUserById() {
        String body = """
        {
          "firstName": "Updated",
          "lastName": "",
          "email": "",
          "password": "",
          "age": 0,
          "username": ""
        }
        """;

        given()
                .contentType(ContentType.JSON)
                .pathParam("id", createdUserId)
                .body(body)
                .when()
                .put("/AppUser/{id}")
                .then()
                .statusCode(200)
                .body("firstName", equalTo("Updated"))
                .body("username", equalTo("jake_test")); // unchanged
    }

    // -------------------------------------------
    // 4. UPDATE USER BY USERNAME
    // -------------------------------------------
    @Test
    @Order(4)
    public void testUpdateUserByUsername() {
        String body = """
        {
          "email": "updatedEmail@example.com",
          "firstName": "",
          "lastName": "",
          "password": "",
          "age": 0,
          "username": ""
        }
        """;

        given()
                .contentType(ContentType.JSON)
                .pathParam("username", "jake_test")
                .body(body)
                .when()
                .put("/AppUser/username/{username}")
                .then()
                .statusCode(200)
                .body("email", equalTo("updatedEmail@example.com"));
    }

    // -------------------------------------------
    // 5. DELETE USER BY USERNAME
    // -------------------------------------------
    @Test
    @Order(5)
    public void testDeleteUserByUsername() {
        given()
                .pathParam("username", "jake_test")
                .when()
                .delete("/AppUser/username/{username}")
                .then()
                .statusCode(200)
                .body("message", equalTo("success"));
    }

    // -------------------------------------------
    // 6. DELETE USER BY ID (still valid)
    // -------------------------------------------
    @Test
    @Order(6)
    public void testDeleteUserById() {
        given()
                .pathParam("id", createdUserId)
                .when()
                .delete("/AppUser/{id}")
                .then()
                .statusCode(200)
                .body("message", equalTo("success"));
    }
}

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MovieHubTest {
    private static final String BASE = "http://localhost:8080";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        server = new MoviesServer();
        server.start();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void beforeEach() {
        server.getStore().clear();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> response = send("GET", "/movies", null, null);

        assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        assertEquals(0, JsonParser.parseString(response.body()).getAsJsonArray().size());
    }

    @Test
    void postMovie_whenDataIsValid_createsMovieWithId() throws Exception {
        HttpResponse<String> response = postMovie("Интерстеллар", 2014);

        assertEquals(201, response.statusCode());
        assertJsonContentType(response);
        JsonObject movie = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, movie.get("id").getAsInt());
        assertEquals("Интерстеллар", movie.get("title").getAsString());
        assertEquals(2014, movie.get("year").getAsInt());
    }

    @Test
    void getMovies_whenMoviesExist_returnsAllMovies() throws Exception {
        postMovie("Интерстеллар", 2014);
        postMovie("Дюна", 2021);

        HttpResponse<String> response = send("GET", "/movies", null, null);

        assertEquals(200, response.statusCode());
        JsonArray movies = JsonParser.parseString(response.body()).getAsJsonArray();
        assertEquals(2, movies.size());
        assertEquals("Интерстеллар", movies.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("Дюна", movies.get(1).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void postMovie_whenTitleIsBlank_returnsValidationError() throws Exception {
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"   \",\"year\":2020}", "application/json");

        assertValidationError(response, "название не должно быть пустым");
    }

    @Test
    void postMovie_whenTitleIsTooLong_returnsValidationError() throws Exception {
        String longTitle = "а".repeat(101);
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"" + longTitle + "\",\"year\":2020}", "application/json");

        assertValidationError(response, "название не должно быть длиннее 100 символов");
    }

    @Test
    void postMovie_whenYearIsTooEarly_returnsValidationError() throws Exception {
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"Фильм\",\"year\":1887}", "application/json");

        assertValidationError(response, "год должен быть между 1888 и " + (Year.now().getValue() + 1));
    }

    @Test
    void postMovie_whenYearIsTooLate_returnsValidationError() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"Фильм\",\"year\":" + invalidYear + "}",
                "application/json");

        assertValidationError(response, "год должен быть между 1888 и " + (Year.now().getValue() + 1));
    }

    @Test
    void postMovie_whenContentTypeIsWrong_returnsUnsupportedMediaType() throws Exception {
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"Фильм\",\"year\":2020}", "text/plain");

        assertEquals(415, response.statusCode());
        assertError(response, "Неподдерживаемый тип содержимого");
    }

    @Test
    void postMovie_whenJsonIsMalformed_returnsBadRequest() throws Exception {
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":\"Фильм\",", "application/json");

        assertEquals(400, response.statusCode());
        assertError(response, "Некорректный JSON");
    }

    @Test
    void postMovie_whenFieldTypeIsWrong_returnsBadRequest() throws Exception {
        HttpResponse<String> response = send(
                "POST", "/movies", "{\"title\":123,\"year\":2020}", "application/json");

        assertEquals(400, response.statusCode());
        assertError(response, "Некорректный JSON");
    }

    @Test
    void getMovie_whenIdExists_returnsMovie() throws Exception {
        postMovie("Матрица", 1999);

        HttpResponse<String> response = send("GET", "/movies/1", null, null);

        assertEquals(200, response.statusCode());
        JsonObject movie = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, movie.get("id").getAsInt());
        assertEquals("Матрица", movie.get("title").getAsString());
    }

    @Test
    void getMovie_whenIdDoesNotExist_returnsNotFound() throws Exception {
        HttpResponse<String> response = send("GET", "/movies/999", null, null);

        assertEquals(404, response.statusCode());
        assertError(response, "Фильм не найден");
    }

    @Test
    void getMovie_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> response = send("GET", "/movies/not-a-number", null, null);

        assertEquals(400, response.statusCode());
        assertError(response, "Некорректный ID");
    }

    @Test
    void deleteMovie_whenIdExists_removesMovie() throws Exception {
        postMovie("Матрица", 1999);

        HttpResponse<String> deleteResponse = send("DELETE", "/movies/1", null, null);
        HttpResponse<String> getResponse = send("GET", "/movies/1", null, null);

        assertEquals(204, deleteResponse.statusCode());
        assertJsonContentType(deleteResponse);
        assertTrue(deleteResponse.body().isEmpty());
        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void deleteMovie_whenIdDoesNotExist_returnsNotFound() throws Exception {
        HttpResponse<String> response = send("DELETE", "/movies/999", null, null);

        assertEquals(404, response.statusCode());
        assertError(response, "Фильм не найден");
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> response = send("DELETE", "/movies/not-a-number", null, null);

        assertEquals(400, response.statusCode());
        assertError(response, "Некорректный ID");
    }

    @Test
    void getMovies_whenYearMatches_returnsOnlyMatchingMovies() throws Exception {
        postMovie("Фильм 2020", 2020);
        postMovie("Другой фильм 2020", 2020);
        postMovie("Фильм 2021", 2021);

        HttpResponse<String> response = send("GET", "/movies?year=2020", null, null);

        assertEquals(200, response.statusCode());
        JsonArray movies = JsonParser.parseString(response.body()).getAsJsonArray();
        assertEquals(2, movies.size());
        assertTrue(movies.toString().contains("Фильм 2020"));
        assertFalse(movies.toString().contains("Фильм 2021"));
    }

    @Test
    void getMovies_whenYearHasNoMatches_returnsEmptyArray() throws Exception {
        postMovie("Фильм", 2020);

        HttpResponse<String> response = send("GET", "/movies?year=1999", null, null);

        assertEquals(200, response.statusCode());
        assertEquals(0, JsonParser.parseString(response.body()).getAsJsonArray().size());
    }

    @Test
    void getMovies_whenYearIsNotNumber_returnsBadRequest() throws Exception {
        HttpResponse<String> response = send("GET", "/movies?year=unknown", null, null);

        assertEquals(400, response.statusCode());
        assertError(response, "Некорректный параметр запроса — 'year'");
    }

    @Test
    void moviesEndpoint_whenMethodIsUnsupported_returnsMethodNotAllowed() throws Exception {
        HttpResponse<String> response = send("PUT", "/movies", "{}", "application/json");

        assertEquals(405, response.statusCode());
        assertEquals("GET, POST, DELETE", response.headers().firstValue("Allow").orElse(""));
        assertError(response, "Метод не поддерживается");
    }

    private HttpResponse<String> postMovie(String title, int year) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"year\":" + year + "}";
        return send("POST", "/movies", body, "application/json; charset=UTF-8");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path));

        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private void assertValidationError(HttpResponse<String> response, String expectedDetail) {
        assertEquals(422, response.statusCode());
        JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", error.get("error").getAsString());
        assertTrue(error.getAsJsonArray("details").toString().contains(expectedDetail));
    }

    private void assertError(HttpResponse<String> response, String expectedError) {
        assertJsonContentType(response);
        JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(expectedError, error.get("error").getAsString());
    }

    private void assertJsonContentType(HttpResponse<String> response) {
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));
    }
}

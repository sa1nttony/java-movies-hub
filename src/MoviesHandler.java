import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
    private static final int EARLIEST_MOVIE_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store, Gson gson) {
        super(gson);
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
                    sendError(exchange, 405, "Метод не поддерживается");
            }
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        Optional<String> idPart = getIdPart(exchange);
        if (idPart.isPresent()) {
            Integer id = parseId(idPart.get(), exchange);
            if (id == null) {
                return;
            }

            Optional<Movie> movie = store.findById(id);
            if (movie.isPresent()) {
                sendJson(exchange, 200, movie.get());
            } else {
                sendError(exchange, 404, "Фильм не найден");
            }
            return;
        }

        if (!isCollectionPath(exchange)) {
            sendError(exchange, 404, "Ресурс не найден");
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            sendJson(exchange, 200, store.getAll());
            return;
        }

        Integer year = parseYearQuery(query, exchange);
        if (year != null) {
            sendJson(exchange, 200, store.findByYear(year));
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!isCollectionPath(exchange)) {
            sendError(exchange, 404, "Ресурс не найден");
            return;
        }

        if (!hasJsonContentType(exchange)) {
            sendError(exchange, 415, "Неподдерживаемый тип содержимого");
            return;
        }

        JsonElement requestJson;
        try (Reader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            requestJson = JsonParser.parseReader(reader);
        } catch (JsonParseException exception) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        if (!isValidMovieObject(requestJson)) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        Movie movie = gson.fromJson(requestJson, Movie.class);

        List<String> validationErrors = validate(movie);
        if (!validationErrors.isEmpty()) {
            sendError(exchange, 422, "Ошибка валидации", validationErrors);
            return;
        }

        sendJson(exchange, 201, store.add(movie));
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        Optional<String> idPart = getIdPart(exchange);
        if (idPart.isEmpty()) {
            sendError(exchange, 405, "Метод не поддерживается");
            return;
        }

        Integer id = parseId(idPart.get(), exchange);
        if (id == null) {
            return;
        }

        if (store.deleteById(id)) {
            sendNoContent(exchange);
        } else {
            sendError(exchange, 404, "Фильм не найден");
        }
    }

    private boolean isCollectionPath(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return "/movies".equals(path) || "/movies/".equals(path);
    }

    private Optional<String> getIdPart(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/movies/")) {
            return Optional.empty();
        }

        String idPart = path.substring("/movies/".length());
        if (idPart.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(idPart);
    }

    private Integer parseId(String value, HttpExchange exchange) throws IOException {
        if (value.contains("/")) {
            sendError(exchange, 400, "Некорректный ID");
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Некорректный ID");
            return null;
        }
    }

    private Integer parseYearQuery(String query, HttpExchange exchange) throws IOException {
        String[] parameters = query.split("&", -1);
        if (parameters.length != 1) {
            sendInvalidYearQuery(exchange);
            return null;
        }

        String[] nameAndValue = parameters[0].split("=", -1);
        if (nameAndValue.length != 2 || !"year".equals(nameAndValue[0]) || nameAndValue[1].isEmpty()) {
            sendInvalidYearQuery(exchange);
            return null;
        }

        try {
            return Integer.parseInt(nameAndValue[1]);
        } catch (NumberFormatException exception) {
            sendInvalidYearQuery(exchange);
            return null;
        }
    }

    private void sendInvalidYearQuery(HttpExchange exchange) throws IOException {
        sendError(exchange, 400, "Некорректный параметр запроса — 'year'");
    }

    private boolean hasJsonContentType(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return false;
        }

        String mediaType = contentType.split(";", 2)[0].trim();
        return "application/json".equalsIgnoreCase(mediaType);
    }

    private boolean isValidMovieObject(JsonElement requestJson) {
        if (requestJson == null || !requestJson.isJsonObject()) {
            return false;
        }

        JsonObject object = requestJson.getAsJsonObject();
        if (object.has("title") && !object.get("title").isJsonNull()) {
            JsonElement title = object.get("title");
            if (!title.isJsonPrimitive() || !title.getAsJsonPrimitive().isString()) {
                return false;
            }
        }

        if (object.has("year") && !object.get("year").isJsonNull()) {
            JsonElement year = object.get("year");
            if (!year.isJsonPrimitive() || !year.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }

    private List<String> validate(Movie movie) {
        List<String> errors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("название не должно быть длиннее 100 символов");
        }

        int latestYear = Year.now().getValue() + 1;
        if (movie.getYear() < EARLIEST_MOVIE_YEAR || movie.getYear() > latestYear) {
            errors.add("год должен быть между " + EARLIEST_MOVIE_YEAR + " и " + latestYear);
        }
        return Collections.unmodifiableList(errors);
    }
}

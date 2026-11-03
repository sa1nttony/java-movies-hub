import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson;

    protected BaseHttpHandler(Gson gson) {
        this.gson = gson;
    }

    protected void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    protected void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        sendJson(exchange, status, gson.toJson(value));
    }

    protected void sendError(HttpExchange exchange, int status, String error) throws IOException {
        sendJson(exchange, status, new ErrorResponse(error));
    }

    protected void sendError(HttpExchange exchange, int status, String error, List<String> details)
            throws IOException {
        sendJson(exchange, status, new ErrorResponse(error, details));
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}

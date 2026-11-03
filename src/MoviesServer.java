import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private static final int DEFAULT_PORT = 8080;

    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer() {
        this(DEFAULT_PORT);
    }

    public MoviesServer(int port) {
        try {
            store = new MoviesStore();
            Gson gson = new Gson();
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/movies", new MoviesHandler(store, gson));
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось создать HTTP-сервер", exception);
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public MoviesStore getStore() {
        return store;
    }
}

package com.redtoast.simulation;

import com.redtoast.Computer;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.neet.config.ConfigLoader;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.events.EventManager;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Bytes;
import com.redtoast.simulation.value.ValueTypes.Function;
import com.redtoast.simulation.value.ValueTypes.Table;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InternetManager {
    private final ConcurrentLinkedQueue<Request> sendQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, WebSocket> liveSockets = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private double burden = 0;
    private final EventManager eventManager;
    private final Computer computer;
    private static final Map<Integer, String> errorCodes = new HashMap<>();
    private long lastReset = 0;

    static {
        errorCodes.put(200, "OK");
        errorCodes.put(408, "TIMEOUT");
        errorCodes.put(429, "TOO MANY REQUESTS");
        errorCodes.put(400, "BAD REQUEST");
        errorCodes.put(404, "NOT FOUND");
        errorCodes.put(418, "I'M A TEAPOT");
        errorCodes.put(403, "FORBIDDEN");
        errorCodes.put(401, "UNAUTHORIZED");
        errorCodes.put(502, "BAD GATEWAY");
        errorCodes.put(505, "HTTP Version Not Supported".toUpperCase());
    }

    public InternetManager(EventManager eventManager, Computer computer) {
        this.eventManager = eventManager;
        this.computer = computer;

    }

    public void progress(double delta) {
        burden += getRate() * delta;
        while (burden>=0 && !sendQueue.isEmpty()) {
            Request request = sendQueue.poll();
            burden -= request.getSize();
            request.send();
        }
        if (burden>0) burden = 0;
    }

    public void reset() {
        burden = 0;
        sendQueue.clear();
        lastReset = System.currentTimeMillis();
        liveSockets.forEach((key, value) -> value.sendClose(1001, "Going Away"));
        liveSockets.clear();
    }

    public void queueRequest(Request request) {
        if (getRate()==0) {
            request.send();
        }else{
            sendQueue.add(request);
        }
    }

    public void queueResponse(String label, int id, int errorCode, String message, Table headers, Bytes body) {
        eventManager.queueEvent(new EventGeneric(label, Value.of(id), Value.of(errorCode), Value.of(message), Value.of(headers), Value.of(body)), EventLabel.NETWORK);
    }

    public void queueResponse(String label, int id, int errorCode, String message, Table headers) {
        eventManager.queueEvent(new EventGeneric(label, Value.of(id), Value.of(errorCode), Value.of(message), Value.of(headers)), EventLabel.NETWORK);
    }

    public static int getRate(){
        return (int) ConfigLoader.getServerConfig("internet-rate-limit");
    }

    public static boolean hasAccess() {
        return (boolean) ConfigLoader.getServerConfig("allow-internet-access");
    }

    public static int getBufferCap(){
        return (int) ConfigLoader.getServerConfig("internet-outgoing-buffer-size");
    }

    public static int getSocketCap(){
        return (int) ConfigLoader.getServerConfig("internet-max-sockets");
    }

    public boolean ready() {
        return sendQueue.size()<getBufferCap();
    }

    private int generateID() {
        return new Random().nextInt(Integer.MAX_VALUE);
    }

    public abstract static class Request {
        final int size;
        public Request(int requestSize) {
            this.size = requestSize;
        }

        public int getSize() {return size;}

        abstract void send();
    }

    public int httpGet(URI url, Map<String, String> headers) {
        int id = generateID();
        if (!hasAccess()) {
            queueResponse("HttpResponse", id, 111, "ECONNREFUSED" , new Table());
            return id;
        }
        if (!ready()) {
            queueResponse("HttpResponse", id, 105, "ENOBUFSNO" , new Table());
            return id;
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(url);
            headers.forEach(requestBuilder::header);
            requestBuilder.timeout(Duration.ofSeconds(3));
            requestBuilder.GET();
            HttpRequest request = requestBuilder.build();
            long timeOfSending = System.currentTimeMillis();
            AtomicInteger size = new AtomicInteger(url.toString().length());
            headers.forEach((key, value) -> size.addAndGet(key.length() + value.length()));
            queueRequest(new HttpRequestImplementation(size.get(), id, request, timeOfSending));
        }catch (IllegalArgumentException ignored) {
            queueResponse("HttpResponse", id, 400, "BAD REQUEST" , new Table());
        }
        return id;
    }

    public int httpPost(URI url, Map<String, String> headers, byte[] body) {
        int id = generateID();
        if (!hasAccess()) {
            queueResponse("HttpResponse", id, 111, "ECONNREFUSED" , new Table());
            return id;
        }
        if (!ready()) {
            queueResponse("HttpResponse", id, 105, "ENOBUFSNO" , new Table());
            return id;
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(url);
            headers.forEach(requestBuilder::header);
            requestBuilder.timeout(Duration.ofSeconds(3));
            requestBuilder.POST(body.length==0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            HttpRequest request = requestBuilder.build();
            long timeOfSending = System.currentTimeMillis();
            AtomicInteger size = new AtomicInteger(url.toString().length());
            headers.forEach((key, value) -> size.addAndGet(key.length() + value.length()));
            queueRequest(new HttpRequestImplementation(size.get() + body.length, id, request, timeOfSending));
        }catch (IllegalArgumentException ignored) {
            queueResponse("HttpResponse", id, 400, "BAD REQUEST" , new Table());
        }
        return id;
    }

    public class HttpRequestImplementation extends Request {
        private final int id;
        private final HttpRequest request;
        private final long timeOfSending;

        public HttpRequestImplementation(int requestSize, int id, HttpRequest request, long timeOfSending) {
            super(requestSize);
            this.id = id;
            this.request = request;
            this.timeOfSending = timeOfSending;
        }

        @Override
        void send() {
            try (HttpClient client = HttpClient.newHttpClient()) {
                CompletableFuture<HttpResponse<byte[]>> responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
                responseFuture.thenAccept(response -> {
                    if (timeOfSending >= lastReset) {
                        if (response.body()==null || response.body().length==0) {
                            queueResponse("HttpResponse", id, response.statusCode(), errorCodes.getOrDefault(response.statusCode(), "UNKNOWN STATUS CODE"), fromHeaders(response.headers()));
                        }else{
                            queueResponse("HttpResponse", id, response.statusCode(), errorCodes.getOrDefault(response.statusCode(), "UNKNOWN STATUS CODE"), fromHeaders(response.headers()), new Bytes(response.body()));
                        }
                    }
                }).exceptionally(error -> {
                    httpErrorHandler("HttpResponse", error, id);
                    return null;
                });
            }
        }
    }

    private void httpErrorHandler(String label, Throwable error, int id) {
        if (error instanceof CompletionException completionException) {
            error = completionException.getCause();
        }
        switch (error) {
            case ConnectException ignored -> queueResponse(label, id, 404, "NOT FOUND", new Table());
            case HttpTimeoutException ignored -> queueResponse(label, id, 110, "ETIMEDOUT", new Table());
            case SocketTimeoutException ignored -> queueResponse(label, id, 110, "ETIMEDOUT", new Table());
            case UnknownHostException ignored -> queueResponse(label, id, 404, "NOT FOUND", new Table());
            case IOException ignored -> queueResponse(label, id, 400, "BAD REQUEST", new Table());
            case InterruptedException ignored -> queueResponse(label, id, 409, "CONFLICT", new Table());
            default -> {
                queueResponse(label, id, 0, "UNIDENTIFIED EXCEPTION", new Table());
                if (NeetComputersServer.DO_LOGGING) APILoader.errorLogger.error("Unknown HTTP error encountered");
                APILoader.printJavaError(error);
            }
        }
    }

    private Table fromHeaders(HttpHeaders headers) {
        Table table = new Table();
        headers.map().forEach((key, values) -> table.put(key, StringUtils.join(values, ", ")));
        return table;
    }

    public int requestWebsocket(URI url, Map<String, String> headers) {
        int id = generateID();
        if (!hasAccess()) {
            throw new ExposedError("Internet access disabled");
        }
        if (!(liveSockets.size() < getSocketCap())) {
            throw new ExposedError("Limit on active sockets reached");
        }
        try {
            WebSocket.Builder request = httpClient.newWebSocketBuilder();
            headers.forEach(request::header);
            request.connectTimeout(Duration.ofSeconds(5));

            request.buildAsync(url, new SocketListener(id)).thenAccept(webSocket -> {
                liveSockets.put(id, webSocket);
                if (!(liveSockets.size() < getSocketCap())) {
                    webSocket.sendClose(1008, "Policy Violation");
                }
            });
        }catch (IllegalArgumentException ignored) {
            throw new ExposedError("Invalid Request");
        }catch (CompletionException error) {
            switch (error.getCause()) {
                case WebSocketHandshakeException ignored -> throw new ExposedError("Handshake failed");
                case HttpTimeoutException ignored -> throw new ExposedError("Timed out");
                case SecurityException ignored -> throw new ExposedError("Insecure operation");
                case IOException ignored -> throw new ExposedError("Invalid Request");
                case InterruptedException ignored -> throw new ExposedError("Threading failure");
                case IllegalArgumentException ignored -> throw new ExposedError("Invalid Request");
                default -> {
                    if (NeetComputersServer.DO_LOGGING) APILoader.errorLogger.error("Unknown HTTP error encountered");
                    APILoader.printJavaError(error);
                    throw new ExposedError("Unknown error, check logs");
                }
            }
        }
        return id;
    }

    private class SocketListener implements WebSocket.Listener {
        private final int id;
        public SocketListener(int id){this.id = id;}

        public void receiveMessage(Bytes body, boolean binary) {
            eventManager.queueEvent(new EventGeneric("WebsocketMessage", Value.of(id), Value.of(body), Value.of(binary)), EventLabel.NETWORK);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            byte[] body = new byte[data.length()];
            for (int i = 0; i < data.length(); i++) body[i] = (byte) data.charAt(i);
            receiveMessage(new Bytes(body), false);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] body = new byte[data.remaining()];
            data.get(body);
            receiveMessage(new Bytes(body), true);
            webSocket.request(1);
            return null;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            Runtime runtime = computer.getRuntime();
            eventManager.queueEvent(new EventGeneric("WebsocketOpened", Value.of(id),
                    new Function(false, "send", ParameterBuilder.make(byte[].class, boolean.class)) {
                        @Override
                        public Value call(Value<?>[] parameters) {
                            Optional<String> check = getRules().canCast(parameters, runtime==null ? null : runtime.getThread().getLang());
                            if (check.isPresent()) return Value.asError(check.get());
                            if (!ready()) {
                                eventManager.queueEvent(new EventGeneric("WebsocketSendFailure", Value.of("Outgoing buffer full, try again later")), EventLabel.NETWORK);
                                return Value.NULL;
                            }
                            Object[] args = getRules().cast(parameters, runtime);
                            byte[] data = (byte[]) args[0];
                            queueRequest(new Request(data.length) {
                                @Override
                                void send() {
                                    if (webSocket.isInputClosed() || webSocket.isOutputClosed()) {
                                        eventManager.queueEvent(new EventGeneric("WebsocketSendFailure", Value.of(id), Value.of("Line closed")), EventLabel.NETWORK);
                                    }else{
                                        ((boolean) args[1] ? webSocket.sendBinary(ByteBuffer.wrap(data), true) : webSocket.sendText(new String(data), true)).thenAccept(webSocket2 -> {
                                            eventManager.queueEvent(new EventGeneric("WebsocketSendSuccess", Value.of(id)), EventLabel.NETWORK);
                                        }).exceptionally(error -> {
                                            eventManager.queueEvent(new EventGeneric("WebsocketSendFailure", Value.of(id), Value.of("Line busy, try again later")), EventLabel.NETWORK);
                                            return null;
                                        });
                                    }
                                }
                            });
                            return Value.NULL;
                        }
                    }.asValue(),
                    new Function(false, "close", Parameters.empty()) {
                        @Override
                        public Value call(Value<?>[] parameters) {
                            Optional<String> check = getRules().canCast(parameters, runtime==null ? null : runtime.getThread().getLang());
                            if (check.isPresent()) return Value.asError(check.get());
                            if (webSocket.isInputClosed() || webSocket.isOutputClosed()) {
                                throw new ExposedError("Already closed");
                            }
                            webSocket.sendClose(1000, "Normal Closure");
                            return Value.NULL;
                        }
                    }.asValue()
                    ), EventLabel.NETWORK);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            eventManager.queueEvent(new EventGeneric("WebsocketClosed", Value.of(id), Value.of(statusCode), Value.of(reason)), EventLabel.NETWORK);
            liveSockets.remove(id);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            eventManager.queueEvent(new EventGeneric("WebsocketClosed", Value.of(id), Value.of(1006), Value.of("Abnormal Closure")), EventLabel.NETWORK);
            if (NeetComputersServer.DO_LOGGING) APILoader.errorLogger.error("Unknown Websocket error encountered");
            APILoader.printJavaError(error);
        }
    }
}

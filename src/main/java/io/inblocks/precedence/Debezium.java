package io.inblocks.precedence;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.regex.Pattern;

public class Debezium {

    private static final Logger LOGGER = LoggerFactory.getLogger(Debezium.class);
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final String NAME_SUFFIX = "Key";

    private static String sha256(byte[] data) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(data);
            byte[] digest = messageDigest.digest();
            return DatatypeConverter.printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonObject inputStreamToJson(InputStream inputStream) {
        return JSON_PARSER.parse(new BufferedReader(new InputStreamReader(inputStream))).getAsJsonObject();
    }

    private static Response post(String baseUrl, String id, String chain, JsonElement data) throws Exception {
        byte[] bytes = data.toString().getBytes();
        String url = baseUrl + "/records?id=" + id + "&hash=" + sha256(bytes) + "&chain=" + URLEncoder.encode(chain, "utf-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("content-type", "application/octet-stream");
        connection.setDoOutput(true);
        connection.getOutputStream().write(bytes);
        connection.connect();
        JsonObject responseBody;
        try {
            responseBody = inputStreamToJson(connection.getInputStream());
        } catch (Exception e1) {
            responseBody = inputStreamToJson(connection.getErrorStream());
        }
        return new Response(url, connection.getResponseCode(), responseBody);
    }

    public static void main(String[] args) {
        String applicationId = System.getenv("APPLICATION_ID");
        String bootstrapServers = System.getenv("BOOTSTRAP_SERVERS");
        String inputTopicRegex = System.getenv("INPUT_TOPIC_PATTERN");
        String precedenceApi = System.getenv("PRECEDENCE_API");
        if (bootstrapServers == null || applicationId == null || inputTopicRegex == null || precedenceApi == null) {
            System.err.println("The following environment variables must be defined:\n" +
                    "APPLICATION_ID\n" +
                    "BOOTSTRAP_SERVERS\n" +
                    "INPUT_TOPIC_PATTERN\n" +
                    "PRECEDENCE_API");
            System.exit(1);
        }

        LOGGER.info("Environment variables:\n" +
                "\tAPPLICATION_ID: " + applicationId + "\n" +
                "\tBOOTSTRAP_SERVERS: " + bootstrapServers + "\n" +
                "\tINPUT_TOPIC_PATTERN: " + inputTopicRegex + "\n" +
                "\tPRECEDENCE_API: " + precedenceApi + "\n");

        StreamsBuilder streamsBuilder = new StreamsBuilder();
        streamsBuilder.stream(Pattern.compile(inputTopicRegex)).filter((key, value) -> value != null).foreach((key, value) -> {
            // key
            JsonObject keyJson = JSON_PARSER.parse(key.toString()).getAsJsonObject();
            String name = keyJson.getAsJsonObject("schema").get("name").getAsString();
            JsonObject payload = keyJson.getAsJsonObject("payload");
            JsonObject sortedPayload = new JsonObject();
            payload.keySet().stream().sorted().forEach(s -> sortedPayload.add(s, payload.get(s)));
            if (!name.endsWith(NAME_SUFFIX)) {
                throw new RuntimeException("");
            }
            // value
            String stringValue = value.toString();
            JsonElement after = JSON_PARSER.parse(stringValue).getAsJsonObject().getAsJsonObject("payload").get("after");

            String id = sha256(stringValue.getBytes());
            String chain = name.substring(0, name.length() - NAME_SUFFIX.length()) + sortedPayload.toString();
            JsonElement data = after.isJsonNull() ? JsonNull.INSTANCE : after;
            while (true) {
                try {
                    Response response = post(precedenceApi, id, chain, data);
                    if (response.code == 201) {
                        LOGGER.info(String.format("CREATED id:%s(%s) chain:%s",
                                response.body.getAsJsonObject("data").getAsJsonObject("provable").get("id").getAsString(),
                                id,
                                chain));
                        break;
                    }
                    JsonObject error = response.body.getAsJsonObject("error");
                    if (response.code == 409 && error.get("code").getAsInt() == 3) {
                        LOGGER.warn(String.format("CONFLICT id:%s(%s) chain:%s",
                                error.getAsJsonObject("data").get("id").getAsString(),
                                id,
                                chain));
                        break;
                    }
                    LOGGER.warn(String.format("id:?(%s) chain:%s\n%s\n%s", id, chain, key.toString(), response));
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), key.toString());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.info(e.getMessage());
                }
            }
        });

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        KafkaStreams streams = new KafkaStreams(streamsBuilder.build(), properties);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            Runtime.getRuntime().halt(0);
        }));
        try {
            streams.start();
        } catch (Throwable e) {
            System.exit(1);
        }
    }

    public static class Response {

        String url;
        int code;
        JsonObject body;

        Response(String url, int code, JsonObject body) {
            this.url = url;
            this.code = code;
            this.body = body;
        }

        @Override
        public String toString() {
            return url + " " + code + "\n" + body;
        }

    }

}

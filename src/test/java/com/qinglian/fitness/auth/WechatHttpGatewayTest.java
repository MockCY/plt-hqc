package com.qinglian.fitness.auth;

import com.qinglian.fitness.config.WechatProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WechatHttpGatewayTest {

    @Test
    void parsesJsonReturnedAsTextPlain() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] response = """
                {"openid":"openid-1","unionid":"unionid-1","session_key":"session-key"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            WechatProperties properties = new WechatProperties(
                "app-id",
                "app-secret",
                "http://127.0.0.1:" + server.getAddress().getPort()
            );
            WechatHttpGateway gateway = new WechatHttpGateway(
                properties,
                RestClient.builder(),
                new ObjectMapper()
            );

            WechatIdentity identity = gateway.exchangeLoginCode("login-code");

            assertThat(identity.openId()).isEqualTo("openid-1");
            assertThat(identity.unionId()).isEqualTo("unionid-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsPhoneCodeAsJson() throws Exception {
        AtomicReference<String> phoneContentType = new AtomicReference<>();
        AtomicReference<String> phoneContentLength = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/cgi-bin/token", exchange -> respondTextPlain(exchange, """
            {"access_token":"access-token","expires_in":7200}
            """));
        server.createContext("/wxa/business/getuserphonenumber", exchange -> {
            phoneContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            phoneContentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            respondTextPlain(exchange, """
                {"errcode":0,"errmsg":"ok","phone_info":{"phoneNumber":"+8613812345678","purePhoneNumber":"13812345678","countryCode":"86"}}
                """);
        });
        server.start();

        try {
            WechatProperties properties = new WechatProperties(
                "app-id",
                "app-secret",
                "http://127.0.0.1:" + server.getAddress().getPort()
            );
            WechatHttpGateway gateway = new WechatHttpGateway(
                properties,
                RestClient.builder(),
                new ObjectMapper()
            );

            WechatPhone phone = gateway.exchangePhoneCode("phone-code");

            assertThat(phone.purePhoneNumber()).isEqualTo("13812345678");
            assertThat(phoneContentType.get()).startsWith("application/json");
            assertThat(phoneContentLength.get()).isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    private static void respondTextPlain(com.sun.net.httpserver.HttpExchange exchange, String body)
        throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}

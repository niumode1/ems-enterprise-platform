package com.company.ems.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public RealtimeConfig(RealtimeWebSocketHandler handler,
                          RealtimeHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/ws")
                .addInterceptors(handshakeInterceptor)
                // Origin cannot be fixed at startup because deployments differ. The
                // handshake still requires a valid access token and an active account.
                .setAllowedOriginPatterns("*");
    }
}

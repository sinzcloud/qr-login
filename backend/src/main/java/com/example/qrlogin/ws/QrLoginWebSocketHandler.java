package com.example.qrlogin.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class QrLoginWebSocketHandler extends TextWebSocketHandler {

    // Key: ticket, Value: WebSocketSession
    private static final ConcurrentHashMap<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 URL 参数中获取 ticket, e.g., ws://localhost:8080/ws/qr-login?ticket=xxx
        String ticket = getTicketFromSession(session);
        
        if (ticket != null && !ticket.isEmpty()) {
            log.info("WebSocket 连接建立: ticket={}", ticket);
            
            // 【关键步骤】：如果该 ticket 已有旧连接，强制关闭旧连接
            WebSocketSession oldSession = SESSION_MAP.put(ticket, session);
            if (oldSession != null && oldSession.isOpen()) {
                log.warn("检测到重复连接，关闭旧会话: ticket={}", ticket);
                try {
                    oldSession.close(CloseStatus.NORMAL.withReason("新连接已建立"));
                } catch (Exception e) {
                    log.error("关闭旧会话失败", e);
                }
            }
        } else {
            log.warn("WebSocket 连接缺少 ticket 参数");
            session.close(CloseStatus.BAD_DATA.withReason("Missing ticket parameter"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 处理前端发来的消息（如果需要双向通信）
        // 目前主要是后端推送，这里可以留空或处理心跳
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String ticket = getTicketFromSession(session);
        if (ticket != null) {
            SESSION_MAP.remove(ticket);
            log.info("WebSocket 连接关闭: ticket={}, status={}", ticket, status);
        }
    }

    /**
     * 向指定 ticket 的客户端发送消息
     */
    public void send(String ticket, String message) {
        WebSocketSession session = SESSION_MAP.get(ticket);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("发送 WebSocket 消息失败: ticket={}", ticket, e);
                // 发送失败可能意味着连接已断开，清理映射
                SESSION_MAP.remove(ticket);
            }
        } else {
            log.debug("WebSocket 会话不存在或已关闭: ticket={}", ticket);
        }
    }

    private String getTicketFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("ticket=")) {
                    return param.substring(7);
                }
            }
        }
        return null;
    }
}
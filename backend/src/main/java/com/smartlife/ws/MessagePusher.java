package com.smartlife.ws;

import com.smartlife.common.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息推送器：统一 WebSocket 下行消息结构与入口
 *
 * 三端消息路由：
 *   ORDER_STATUS    -> 下单用户（状态变更）
 *   NEW_ORDER       -> 该订单所属店铺的商家（新单提醒/响铃）
 *   SECKILL_RESULT  -> 抢券用户
 */
@Slf4j
@Component
public class MessagePusher {

    public static final String TYPE_ORDER_STATUS = "ORDER_STATUS";
    public static final String TYPE_NEW_ORDER = "NEW_ORDER";
    public static final String TYPE_SECKILL_RESULT = "SECKILL_RESULT";
    public static final String TYPE_UV_ALERT = "UV_ALERT";

    private static MessagePusher INSTANCE;

    private final WsSessionManager sessionManager;

    public MessagePusher(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        INSTANCE = this;
    }

    public static MessagePusher pusher() {
        return INSTANCE;
    }

    /** 订单状态变更 -> 推送给下单用户 */
    public void orderStatus(Long userId, Map<String, Object> data) {
        sendToUser(userId, TYPE_ORDER_STATUS, data);
    }

    /**
     * 新订单 -> 只推送给该店铺的商家（数据隔离：A 店的新单不会打扰 B 店）
     * 平台管理员不在推送范围内，避免新单噪音。
     */
    public void newOrderToMerchant(Long merchantId, Map<String, Object> data) {
        if (merchantId == null) {
            return;
        }
        sessionManager.sendToMerchant(merchantId, wrap(TYPE_NEW_ORDER, data));
    }

    /** 秒杀异步下单结果 -> 推送给用户 */
    public void seckillResult(Long userId, boolean success, String msg) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("msg", msg);
        sendToUser(userId, TYPE_SECKILL_RESULT, data);
    }

    private void sendToUser(Long userId, String type, Map<String, Object> data) {
        sessionManager.sendToUser(userId, wrap(type, data));
    }

    private String wrap(String type, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("data", data == null ? new HashMap<>() : data);
        payload.put("ts", System.currentTimeMillis());
        return JsonUtils.toJson(payload);
    }
}

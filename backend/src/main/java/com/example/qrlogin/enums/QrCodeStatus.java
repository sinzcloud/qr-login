package com.example.qrlogin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * 二维码登录状态枚举
 */
@Getter
@AllArgsConstructor
public enum QrCodeStatus {
    // 1. 先定义所有常量，此时不传入 nextAllowedStates，或者传入空列表占位
    WAITING(0, "等待扫描"),
    SCANNED(1, "已扫描，待确认"),
    CONFIRMED(2, "已确认登录"),
    EXPIRED(3, "已过期"),
    CANCELLED(4, "已取消/拒绝");

    private final int code;
    private final String desc;
    
    // 2. 使用静态 Map 存储流转规则，避免构造时的前向引用问题
    private static final Map<QrCodeStatus, Set<QrCodeStatus>> TRANSITION_RULES = new EnumMap<>(QrCodeStatus.class);

    static {
        // 3. 在静态块中初始化流转规则，此时所有枚举实例已创建完毕
        TRANSITION_RULES.put(WAITING, EnumSet.of(SCANNED, CANCELLED, EXPIRED));
        TRANSITION_RULES.put(SCANNED, EnumSet.of(CONFIRMED, CANCELLED, EXPIRED));
        
        // 终态没有后续状态，放入空集合或不放均可，这里放入空集合以保持一致性
        TRANSITION_RULES.put(CONFIRMED, Collections.emptySet());
        TRANSITION_RULES.put(EXPIRED, Collections.emptySet());
        TRANSITION_RULES.put(CANCELLED, Collections.emptySet());
    }

    /**
     * 校验当前状态是否可以流转到目标状态
     * @param nextState 目标状态
     * @return true 如果允许流转
     */
    public boolean canTransitionTo(QrCodeStatus nextState) {
        if (nextState == null) {
            return false;
        }
        Set<QrCodeStatus> allowedStates = TRANSITION_RULES.get(this);
        return allowedStates != null && allowedStates.contains(nextState);
    }

    public static QrCodeStatus getByCode(int code) {
        for (QrCodeStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
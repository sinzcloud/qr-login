package com.example.qrlogin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QrCreateVO {
    private String ticket;
    private String qrUrl;
    private Integer expireSeconds;
    private String wsUrl;
}

package com.example.qrlogin.dto;

import lombok.Data;

/**
 * 二维码取消/拒绝登录请求 DTO
 */
@Data
public class QrCancelDTO {
    /**
     * 二维码票据
     */
    private String ticket;
}
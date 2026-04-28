package com.example.qrlogin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QrStatusVO {
    private Integer status;
    private String token;
}

package com.example.qrlogin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qr_login_record")
public class QrLoginRecord {
    private Long id;
    private String ticket;
    private Long userId;
    private Integer status;
    private String pcIp;
    private String scanIp;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.example.qrlogin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.qrlogin.common.BizException;
import com.example.qrlogin.entity.QrLoginRecord;
import com.example.qrlogin.entity.SysUser;
import com.example.qrlogin.enums.QrCodeStatus;
import com.example.qrlogin.mapper.QrLoginRecordMapper;
import com.example.qrlogin.mapper.SysUserMapper;
import com.example.qrlogin.vo.QrCreateVO;
import com.example.qrlogin.vo.QrStatusVO;
import com.example.qrlogin.ws.QrLoginWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QrLoginService {
    private final StringRedisTemplate redis;
    private final QrLoginRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final QrLoginWebSocketHandler wsHandler;

    @Value("${app.qr-login.expire-seconds}")
    private int expireSeconds;
    @Value("${app.qr-login.mobile-confirm-url}")
    private String mobileConfirmUrl;

    private static final String K_STATUS_PREFIX = "qr:login:status:";
    private static final String K_TOKEN_PREFIX = "qr:login:token:";
    private static final String K_IP_TICKET_PREFIX = "qr:login:ip:ticket:";

    private String kStatus(String ticket) { return K_STATUS_PREFIX + ticket; }
    private String kToken(String ticket) { return K_TOKEN_PREFIX + ticket; }
    private String kIpTicket(String ip) { return K_IP_TICKET_PREFIX + ip; }

    public QrCreateVO create(String pcIp) {
        // 1. 检查并失效该 IP 下的旧 Ticket
        String oldTicket = redis.opsForValue().get(kIpTicket(pcIp));
        if (oldTicket != null) {
            invalidateTicket(oldTicket);
        }

        // 2. 生成新 Ticket
        String newTicket = UUID.randomUUID().toString().replace("-", "");
        
        // 3. 初始化状态
        QrCodeStatus initialStatus = QrCodeStatus.WAITING;
        redis.opsForValue().set(kStatus(newTicket), String.valueOf(initialStatus.getCode()), expireSeconds, TimeUnit.SECONDS);
        
        // 4. 绑定 IP
        redis.opsForValue().set(kIpTicket(pcIp), newTicket, expireSeconds, TimeUnit.SECONDS);

        // 5. 数据库记录
        QrLoginRecord r = new QrLoginRecord();
        r.setTicket(newTicket);
        r.setStatus(initialStatus.getCode());
        r.setPcIp(pcIp);
        r.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(r);

        String qrUrl = mobileConfirmUrl + "?ticket=" + newTicket;
        String wsUrl = "ws://localhost:8080/ws/qr-login?ticket=" + newTicket;
        return new QrCreateVO(newTicket, qrUrl, expireSeconds, wsUrl);
    }

    private void invalidateTicket(String ticket) {
        // 尝试将旧票标记为取消，如果已经是终态则忽略
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        if (statusStr != null) {
            QrCodeStatus currentStatus = QrCodeStatus.getByCode(Integer.parseInt(statusStr));
            if (currentStatus != null && currentStatus.canTransitionTo(QrCodeStatus.CANCELLED)) {
                redis.opsForValue().set(kStatus(ticket), String.valueOf(QrCodeStatus.CANCELLED.getCode()), expireSeconds, TimeUnit.SECONDS);
                updateRecordStatus(ticket, QrCodeStatus.CANCELLED.getCode());
                wsHandler.send(ticket, "{\"status\":" + QrCodeStatus.CANCELLED.getCode() + "}");
            }
        }
    }

    public QrStatusVO status(String ticket) {
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        if (statusStr == null) {
            markExpired(ticket);
            return new QrStatusVO(QrCodeStatus.EXPIRED.getCode(), null);
        }
        
        int statusCode = Integer.parseInt(statusStr);
        QrCodeStatus statusEnum = QrCodeStatus.getByCode(statusCode);
        
        if (statusEnum == null || statusEnum == QrCodeStatus.EXPIRED) {
             return new QrStatusVO(QrCodeStatus.EXPIRED.getCode(), null);
        }

        String token = null;
        if (statusEnum == QrCodeStatus.CONFIRMED) {
            token = redis.opsForValue().get(kToken(ticket));
        }
        
        return new QrStatusVO(statusCode, token);
    }

    public void scan(String ticket, String scanIp) {
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        if (statusStr == null) throw new BizException("二维码已过期");
        
        QrCodeStatus currentStatus = QrCodeStatus.getByCode(Integer.parseInt(statusStr));
        if (currentStatus == null) throw new BizException("状态数据异常");

        // 【核心优化】使用枚举校验流转合法性
        if (!currentStatus.canTransitionTo(QrCodeStatus.SCANNED)) {
            throw new BizException("当前状态不允许扫描: " + currentStatus.getDesc());
        }

        // 执行变更
        redis.opsForValue().set(kStatus(ticket), String.valueOf(QrCodeStatus.SCANNED.getCode()), expireSeconds, TimeUnit.SECONDS);
        updateRecord(ticket, null, QrCodeStatus.SCANNED.getCode(), scanIp);
        wsHandler.send(ticket, "{\"status\":" + QrCodeStatus.SCANNED.getCode() + "}");
    }

    public void confirm(String ticket, Long userId) {
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        if (statusStr == null) throw new BizException("二维码已过期");
        
        QrCodeStatus currentStatus = QrCodeStatus.getByCode(Integer.parseInt(statusStr));
        if (currentStatus == null) throw new BizException("状态数据异常");

        // 【核心优化】使用枚举校验流转合法性
        if (!currentStatus.canTransitionTo(QrCodeStatus.CONFIRMED)) {
            throw new BizException("当前状态不允许确认: " + currentStatus.getDesc());
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException("用户不存在");

        String token = jwtService.createToken(user.getId(), user.getUsername());
        
        redis.opsForValue().set(kStatus(ticket), String.valueOf(QrCodeStatus.CONFIRMED.getCode()), expireSeconds, TimeUnit.SECONDS);
        redis.opsForValue().set(kToken(ticket), token, expireSeconds, TimeUnit.SECONDS);
        
        updateRecord(ticket, userId, QrCodeStatus.CONFIRMED.getCode(), null);
        wsHandler.send(ticket, "{\"status\":" + QrCodeStatus.CONFIRMED.getCode() + ",\"token\":\"" + token + "\"}");
    }

    @Transactional
    public void cancelLogin(String ticket) {
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        
        QrCodeStatus currentStatus;
        if (statusStr == null) {
            // Redis 缺失，查库兜底
            QrLoginRecord record = recordMapper.selectOne(new LambdaQueryWrapper<QrLoginRecord>().eq(QrLoginRecord::getTicket, ticket));
            if (record == null) throw new BizException("无效的票据");
            currentStatus = QrCodeStatus.getByCode(record.getStatus());
            if (currentStatus == null) throw new BizException("状态数据异常");
            
            // 如果已经是终态，直接返回或抛异常
            if (!currentStatus.canTransitionTo(QrCodeStatus.CANCELLED)) {
                 if (currentStatus == QrCodeStatus.CANCELLED) return; // 幂等处理
                 throw new BizException("登录流程已结束，无法取消");
            }
        } else {
            currentStatus = QrCodeStatus.getByCode(Integer.parseInt(statusStr));
            if (currentStatus == null) throw new BizException("状态数据异常");
            
            // 【核心优化】使用枚举校验流转合法性
            if (!currentStatus.canTransitionTo(QrCodeStatus.CANCELLED)) {
                if (currentStatus == QrCodeStatus.CANCELLED) return; // 幂等处理
                throw new BizException("当前状态不允许取消: " + currentStatus.getDesc());
            }
        }

        // 执行变更
        redis.opsForValue().set(kStatus(ticket), String.valueOf(QrCodeStatus.CANCELLED.getCode()), expireSeconds, TimeUnit.SECONDS);
        updateRecordStatus(ticket, QrCodeStatus.CANCELLED.getCode());
        wsHandler.send(ticket, "{\"status\":" + QrCodeStatus.CANCELLED.getCode() + "}");
    }

    private void markExpired(String ticket) {
        String statusStr = redis.opsForValue().get(kStatus(ticket));
        if (statusStr != null) {
            QrCodeStatus s = QrCodeStatus.getByCode(Integer.parseInt(statusStr));
            if (s != null && (s == QrCodeStatus.EXPIRED || s == QrCodeStatus.CANCELLED || s == QrCodeStatus.CONFIRMED)) {
                return; 
            }
        }

        QrLoginRecord r = recordMapper.selectOne(new LambdaQueryWrapper<QrLoginRecord>().eq(QrLoginRecord::getTicket, ticket));
        if (r != null && r.getStatus() < QrCodeStatus.CONFIRMED.getCode()) {
            r.setStatus(QrCodeStatus.EXPIRED.getCode());
            r.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(r);
        }
    }

    private void updateRecord(String ticket, Long userId, Integer status, String scanIp) {
        QrLoginRecord r = recordMapper.selectOne(new LambdaQueryWrapper<QrLoginRecord>().eq(QrLoginRecord::getTicket, ticket));
        if (r == null) return;
        
        boolean needUpdate = false;
        if (userId != null) { r.setUserId(userId); needUpdate = true; }
        if (scanIp != null) { r.setScanIp(scanIp); needUpdate = true; }
        if (status != null) { r.setStatus(status); needUpdate = true; }
        
        if (needUpdate) {
            r.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(r);
        }
    }

    private void updateRecordStatus(String ticket, Integer status) {
        QrLoginRecord r = recordMapper.selectOne(new LambdaQueryWrapper<QrLoginRecord>().eq(QrLoginRecord::getTicket, ticket));
        if (r != null) {
            r.setStatus(status);
            r.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(r);
        }
    }
}
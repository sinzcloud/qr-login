package com.example.qrlogin.controller;

import com.example.qrlogin.common.Result;
import com.example.qrlogin.dto.QrCancelDTO;
import com.example.qrlogin.dto.QrConfirmDTO;
import com.example.qrlogin.entity.SysUser;
import com.example.qrlogin.service.AuthService;
import com.example.qrlogin.service.QrLoginService;
import com.example.qrlogin.vo.QrCreateVO;
import com.example.qrlogin.vo.QrStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qr-login")
@RequiredArgsConstructor
@Slf4j
public class QrLoginController {
    private final QrLoginService qrLoginService;
    private final AuthService authService;

    @PostMapping("/create")
    public Result<QrCreateVO> create(HttpServletRequest request) {
        return Result.ok(qrLoginService.create(request.getRemoteAddr()));
    }

    @GetMapping("/status")
    public Result<QrStatusVO> status(@RequestParam String ticket) {
        return Result.ok(qrLoginService.status(ticket));
    }

    @PostMapping("/scan")
    public Result<Void> scan(@RequestParam String ticket, HttpServletRequest request) {
        qrLoginService.scan(ticket, request.getRemoteAddr());
        return Result.ok();
    }

    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestBody QrConfirmDTO dto, @RequestHeader("Authorization") String token) {
        SysUser user = authService.current(token);
        qrLoginService.confirm(dto.getTicket(), user.getId());
        return Result.ok();
    }

    /**
     * 取消/拒绝扫码登录
     */
    @PostMapping("/cancel")
    public Result<Void> cancelLogin(@RequestBody QrCancelDTO dto) {
        // 1. 参数校验
        if (dto == null || dto.getTicket() == null || dto.getTicket().isEmpty()) {
            return Result.fail("票据不能为空");
        }

        String ticket = dto.getTicket();

        try {
            // 2. 调用服务层取消逻辑
            qrLoginService.cancelLogin(ticket);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            // 业务异常（如票据不存在、状态不允许取消等）
            log.warn("取消登录业务异常: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            // 系统异常
            log.error("取消登录系统异常", e);
            return Result.fail("取消失败，请稍后重试");
        }
    }
}

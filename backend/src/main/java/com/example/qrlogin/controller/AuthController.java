package com.example.qrlogin.controller;

import com.example.qrlogin.common.Result;
import com.example.qrlogin.dto.LoginDTO;
import com.example.qrlogin.entity.SysUser;
import com.example.qrlogin.service.AuthService;
import com.example.qrlogin.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) { return Result.ok(authService.login(dto)); }

    @GetMapping("/me")
    public Result<SysUser> me(@RequestHeader("Authorization") String token) { return Result.ok(authService.current(token)); }
}

package com.example.qrlogin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.qrlogin.common.BizException;
import com.example.qrlogin.dto.LoginDTO;
import com.example.qrlogin.entity.SysUser;
import com.example.qrlogin.mapper.SysUserMapper;
import com.example.qrlogin.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname());
    }

    public SysUser current(String bearerToken) {
        Long userId = jwtService.parseUserId(cleanToken(bearerToken));
        return userMapper.selectById(userId);
    }

    public String cleanToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) throw new BizException("未登录");
        return bearerToken.replace("Bearer ", "");
    }
}

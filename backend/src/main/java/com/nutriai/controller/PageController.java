package com.nutriai.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 简单页面路由：把无扩展名的页面地址转发到 static 下的 HTML。
 */
@Controller
public class PageController {

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "forward:/reset-password.html";
    }

    /**
     * PWA 清单必须以 application/manifest+json 提供，否则移动端浏览器会忽略 manifest 导致无法安装。
     * Spring 静态资源处理器不认识 .webmanifest 扩展名，默认返回 application/octet-stream，
     * 因此这里用 Controller 显式返回正确 Content-Type。
     */
    @GetMapping("/manifest.webmanifest")
    @ResponseBody
    public ResponseEntity<Resource> manifest() {
        ClassPathResource resource = new ClassPathResource("static/manifest.webmanifest");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .body(resource);
    }
}
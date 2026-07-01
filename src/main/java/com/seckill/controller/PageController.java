package com.seckill.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/seckill")
    public String seckill() {
        return "seckill";
    }

    @GetMapping("/order")
    public String order() {
        return "order";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}

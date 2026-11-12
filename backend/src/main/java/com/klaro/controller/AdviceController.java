package com.klaro.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.service.AdviceService;

/** GET /api/advice returns a list of budgeting recommendations (plain text). */
@RestController
@RequestMapping("/api/advice")
public class AdviceController {

    private final AdviceService advice;

    public AdviceController(AdviceService advice) {
        this.advice = advice;
    }

    @GetMapping
    public List<String> advise() {
        return advice.advise();
    }
}

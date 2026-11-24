package com.wealth.bss.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class SampleController {

    @GetMapping("/sample")
    public String sample() {
        return "karthi working from bff to bss";
    }
}

package com.klaro.service;

import java.util.List;

import org.springframework.stereotype.Service;

/** Placeholder that returns fixed advice. Replace with real budget-based logic. */
@Service
public class StubAdviceService implements AdviceService {

    @Override
    public List<String> advise() {
        return List.of(
                "Food spending is close to your monthly limit. Consider cooking at home more often.",
                "Your electricity bill rose compared to last month. Check for appliances left on.");
    }
}

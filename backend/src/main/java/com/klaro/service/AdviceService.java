package com.klaro.service;

import java.util.List;

/**
 * Produces budgeting recommendations as plain-text sentences.
 *
 * To implement the real version: create a new @Service class implementing this
 * interface, then delete StubAdviceService.
 */
public interface AdviceService {
    List<String> advise();
}

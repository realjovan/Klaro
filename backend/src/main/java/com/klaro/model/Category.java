package com.klaro.model;

/**
 * The fixed set of spending categories shared by receipts, budgets, extraction
 * and analytics. Stored in the database by name (e.g. "FOOD"), so values can be
 * reordered or added safely, but renaming or removing one breaks existing rows.
 * The front-end gets this list from GET /api/categories.
 */
public enum Category {
    FOOD,
    TRANSPORT,
    UTILITIES,
    HEALTH,
    SHOPPING,
    EDUCATION,
    ENTERTAINMENT,
    PERSONAL,
    TRAVEL,

    OTHER
}

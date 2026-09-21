package com.genderreveal.api.page;

public class PageNotFoundException extends RuntimeException {
    public PageNotFoundException(String slug) {
        super("Page not found: " + slug);
    }
}

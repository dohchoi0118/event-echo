package com.genderreveal.api.page;

public class PageNotOpenException extends RuntimeException {
    public PageNotOpenException(String slug) {
        super("Page is not open: " + slug);
    }
}

package com.genderreveal.api.page;

public class SlugAlreadyTakenException extends RuntimeException {
    public SlugAlreadyTakenException(String slug) {
        super("Slug already taken: " + slug);
    }
}

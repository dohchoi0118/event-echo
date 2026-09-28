package com.genderreveal.api.page.slug;

public class SlugAlreadyTakenException extends RuntimeException {
    public SlugAlreadyTakenException(String slug) {
        super("Slug already taken: " + slug);
    }
}

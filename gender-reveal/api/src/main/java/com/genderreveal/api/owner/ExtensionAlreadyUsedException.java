package com.genderreveal.api.owner;

public class ExtensionAlreadyUsedException extends RuntimeException {
    public ExtensionAlreadyUsedException(String slug) {
        super("Retention extension already used for page: " + slug);
    }
}

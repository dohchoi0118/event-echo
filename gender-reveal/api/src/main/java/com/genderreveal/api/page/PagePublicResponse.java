package com.genderreveal.api.page;

import java.time.LocalDate;

public record PagePublicResponse(
    String status,
    String nickname,
    String actualGender,
    LocalDate dueDate,
    String message,
    String theme,
    Boolean bgmEnabled
) {
    static PagePublicResponse secretOrExpired(String status, String nickname) {
        return new PagePublicResponse(status, nickname, null, null, null, null, null);
    }

    static PagePublicResponse open(Page page) {
        return new PagePublicResponse(
            "open", page.getNickname(), page.getActualGender(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled()
        );
    }
}

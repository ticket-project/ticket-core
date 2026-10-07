package com.ticket.show.usecase;

import org.jspecify.annotations.Nullable;

/** 공연 원본 이미지 경로를 목록 카드용 이미지 경로로 바꾼다. 상태가 없는 순수 함수다. */
final class ShowCardImagePathConverter {
    private static final String SHOW_IMAGE_PREFIX = "/api/images/shows/";
    private static final String CARD_IMAGE_PREFIX = "/api/images/shows/card/";

    private ShowCardImagePathConverter() {}

    static @Nullable String toCardImage(final @Nullable String imagePath) {
        if (imagePath == null || !imagePath.startsWith(SHOW_IMAGE_PREFIX)) {
            return imagePath;
        }

        final String fileName = imagePath.substring(SHOW_IMAGE_PREFIX.length());
        if (fileName.startsWith("card/")) {
            return imagePath;
        }

        final int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0) {
            return imagePath;
        }

        return CARD_IMAGE_PREFIX + fileName.substring(0, extensionIndex) + ".jpg";
    }
}

package com.ticket.core.domain.show.image;

import org.springframework.stereotype.Component;

@Component
public class ShowCardImagePathConverter {

    private static final String SHOW_IMAGE_PREFIX = "/api/images/shows/";
    private static final String CARD_IMAGE_PREFIX = "/api/images/shows/card/";

    public String toCardImage(final String imagePath) {
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

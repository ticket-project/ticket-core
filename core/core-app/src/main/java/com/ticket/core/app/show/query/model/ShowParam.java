package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.meta.Region;
import lombok.Getter;


@Getter
public class ShowParam {

        private String category;

        private String genre;

        private Region region;

        private String cursor;

    public ShowParam(final String category, final String genre, final Region region, final String cursor) {
        this.category = category;
        this.genre = genre;
        this.region = region;
        this.cursor = cursor;
    }

}

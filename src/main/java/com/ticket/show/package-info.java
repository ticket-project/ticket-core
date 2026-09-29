/** Show BC. */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Show",
        allowedDependencies = {
            "venue :: api",
            "like :: api",
            "member :: api",
            "shared :: api",
            "shared :: web",
            "shared :: exception",
            "shared :: jpa"
        })
package com.ticket.show;

import org.jspecify.annotations.NullMarked;

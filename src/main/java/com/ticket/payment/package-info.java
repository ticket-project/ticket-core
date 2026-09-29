/** Payment BC. Order에 대한 결제 시도의 생명주기를 소유한다. */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {"shared :: jpa"})
package com.ticket.payment;

import org.jspecify.annotations.NullMarked;

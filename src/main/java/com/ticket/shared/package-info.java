/**
 * 다른 module이 <b>호출하는</b> 공유 계약만 두는 leaf module이다. {@code @ApplicationModule}을 명시하는 이유는 annotation이 없으면 javac가
 * {@code package-info.class}를 만들지 않아 Modulith가 이 package를 module로 보지 못하기 때문이고, {@code allowedDependencies = {}}를 명시하는
 * 이유는 leaf 위반을 스냅샷이 아니라 Modulith {@code verify()}가 잡게 하기 위해서다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared",
        allowedDependencies = {})
package com.ticket.shared;

import org.jspecify.annotations.NullMarked;

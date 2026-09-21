/**
 * venue의 JPA 저장·조회 구현이다. 공개 계약({@code venue.api})을 {@code VenueRepositoryAdapter}가 구현하고, 그 안에서 쓰는
 * {@code SpringData*JpaRepository}는 package-private으로 같이 둔다.
 */
@NullMarked
package com.ticket.venue.persistence;

import org.jspecify.annotations.NullMarked;

package com.ticket.show.performance.application;

import java.time.LocalDate;
import java.util.List;

public record PerformanceDateInfo(LocalDate date, List<PerformanceInfo> performances) {
}

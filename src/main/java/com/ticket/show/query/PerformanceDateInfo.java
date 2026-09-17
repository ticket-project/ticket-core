package com.ticket.show.query;

import java.time.LocalDate;
import java.util.List;

public record PerformanceDateInfo(LocalDate date, List<PerformanceInfo> performances) {}

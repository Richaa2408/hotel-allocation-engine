package com.hotel.engine.api;

import com.hotel.engine.service.AnalyticsService;
import com.hotel.engine.service.AnalyticsService.Activity;
import com.hotel.engine.service.AnalyticsService.Analytics;
import com.hotel.engine.service.AnalyticsService.Overview;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Overview dashboard, analytics and the audit log. */
@RestController
public class OverviewController {

    private final AnalyticsService analytics;

    public OverviewController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/overview")
    public Overview overview() {
        return analytics.overview();
    }

    @GetMapping("/api/analytics")
    public Analytics analytics(@RequestParam(defaultValue = "30") int past, @RequestParam(defaultValue = "30") int future) {
        return analytics.analytics(past, future);
    }

    @GetMapping("/api/audit")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<Activity> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return analytics.auditLog(page, size);
    }
}

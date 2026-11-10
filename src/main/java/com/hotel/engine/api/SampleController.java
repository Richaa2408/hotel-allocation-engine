package com.hotel.engine.api;

import com.hotel.engine.sample.SampleDataService;
import com.hotel.engine.sample.SampleDataService.Arrivals;
import com.hotel.engine.sample.SampleDataService.LoadSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sample hotel data for demos: today's arrivals, and a reset back to a known state. */
@RestController
@RequestMapping("/api/sample")
public class SampleController {

    private final SampleDataService samples;

    public SampleController(SampleDataService samples) {
        this.samples = samples;
    }

    /** Today's arriving guests and the rooms free tonight, ready to send to POST /api/assignments. */
    @GetMapping("/arrivals")
    public Arrivals arrivals() {
        return samples.arrivals();
    }

    /** Throws away all bookings and blocklist changes and reloads the sample hotel. */
    @PostMapping("/reset")
    public LoadSummary reset(@RequestParam(required = false) Long seed) {
        return samples.load(seed == null ? samples.defaultSeed() : seed);
    }
}

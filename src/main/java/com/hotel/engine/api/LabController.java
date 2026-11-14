package com.hotel.engine.api;

import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.Room;
import com.hotel.engine.lab.LabService;
import com.hotel.engine.lab.LabService.BloomBenchmark;
import com.hotel.engine.lab.LabService.BloomDemo;
import com.hotel.engine.lab.LabService.BloomSweep;
import com.hotel.engine.lab.LabService.FlowBenchmark;
import com.hotel.engine.lab.LabService.FlowDemo;
import com.hotel.engine.lab.LabService.ScenarioResult;
import com.hotel.engine.lab.LabService.TreeBenchmark;
import com.hotel.engine.lab.LabService.TreeDemo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Algorithm Lab: demos with traces, baseline benchmarks, Bloom filter experiments and scenarios. */
@RestController
@RequestMapping("/api/lab")
public class LabController {

    public record FlowInput(@NotEmpty @Size(max = 150) List<@Valid Guest> guests,
                            @NotEmpty @Size(max = 150) List<@Valid Room> rooms) {
    }

    public record BloomInput(int bitCount, int hashCount, int generated, @Size(max = 50) List<String> insert,
                             @Size(max = 50) List<String> probe, long seed) {
    }

    private final LabService lab;

    public LabController(LabService lab) {
        this.lab = lab;
    }

    @GetMapping("/segment-tree/demo")
    public TreeDemo treeDemo(@RequestParam(defaultValue = "16") int nights, @RequestParam(defaultValue = "12") int rooms,
                             @RequestParam(defaultValue = "3") int from, @RequestParam(defaultValue = "9") int to,
                             @RequestParam(defaultValue = "2") int wanted, @RequestParam(defaultValue = "42") long seed) {
        return lab.segmentTreeDemo(nights, rooms, from, to, wanted, seed);
    }

    @GetMapping("/segment-tree/benchmark")
    public TreeBenchmark treeBenchmark(@RequestParam(defaultValue = "5000") int operations) {
        return lab.segmentTreeBenchmark(operations);
    }

    @GetMapping("/flow/demo")
    public FlowDemo flowDemo(@RequestParam(defaultValue = "greedy-trap") String scenario,
                             @RequestParam(defaultValue = "12") int guests, @RequestParam(defaultValue = "12") int rooms,
                             @RequestParam(defaultValue = "42") long seed) {
        return lab.flowDemo(scenario, guests, rooms, seed);
    }

    @PostMapping("/flow/custom")
    public FlowDemo flowCustom(@Valid @RequestBody FlowInput input) {
        return lab.flowCustom(input.guests(), input.rooms());
    }

    @GetMapping("/flow/benchmark")
    public FlowBenchmark flowBenchmark() {
        return lab.flowBenchmark();
    }

    @PostMapping("/bloom/demo")
    public BloomDemo bloomDemo(@Valid @RequestBody BloomInput b) {
        return lab.bloomDemo(b.bitCount(), b.hashCount(), b.generated(), b.insert(), b.probe(), b.seed());
    }

    @GetMapping("/bloom/sweep")
    public BloomSweep bloomSweep(@RequestParam(defaultValue = "2000") int items) {
        return lab.bloomSweep(items);
    }

    @GetMapping("/bloom/benchmark")
    public BloomBenchmark bloomBenchmark() {
        return lab.bloomBenchmark();
    }

    @GetMapping("/scenarios")
    public List<ScenarioResult> scenarios() {
        return lab.scenarioList();
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioResult scenario(@PathVariable String id) {
        return lab.scenario(id);
    }
}

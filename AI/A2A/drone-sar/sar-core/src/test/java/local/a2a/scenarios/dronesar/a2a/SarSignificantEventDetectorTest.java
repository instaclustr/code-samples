package local.a2a.scenarios.dronesar.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import local.a2a.scenarios.dronesar.model.Detection;
import local.a2a.scenarios.dronesar.model.DroneMode;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;
import local.a2a.scenarios.dronesar.model.Mission;
import local.a2a.scenarios.dronesar.model.Position;
import local.a2a.scenarios.dronesar.sim.MissionLoader;
import local.a2a.scenarios.dronesar.sim.SimulationEngine;
import local.a2a.scenarios.dronesar.sim.SimulationResult;
import org.junit.jupiter.api.Test;

class SarSignificantEventDetectorTest {

    @Test
    void detectsTargetFoundAndTransfer() {
        SarSignificantEventDetector detector = new SarSignificantEventDetector();
        DroneTelemetry first = telemetry(1, "d-01", DroneMode.SEARCH, null, null, null);
        List<SarMissionEvent> start = detector.detect(first, "m1");
        assertEquals(1, start.size());
        assertEquals("MISSION_START", start.get(0).type());

        DroneTelemetry found = telemetry(
                20,
                "d-02",
                DroneMode.SEARCH,
                new Detection("t1", "person", 0.95, List.of()),
                null,
                null);
        List<SarMissionEvent> foundEvents = detector.detect(found, "m1");
        assertEquals(1, foundEvents.size());
        assertEquals("TARGET_FOUND", foundEvents.get(0).type());

        DroneTelemetry transfer = telemetry(21, "d-01", DroneMode.SEARCH, null, "t1", null);
        List<SarMissionEvent> transferEvents = detector.detect(transfer, "m1");
        assertEquals(1, transferEvents.size());
        assertEquals("DRONE_TRANSFER", transferEvents.get(0).type());
    }

    @Test
    void detectsSafetyViolations() {
        SarSignificantEventDetector detector = new SarSignificantEventDetector().configure(40);
        detector.detect(telemetry(1, "d-01", DroneMode.SEARCH, null, null, null), "m1");

        List<SarMissionEvent> low = detector.detectSafety(
                telemetry(5, "d-01", DroneMode.SEARCH, null, null, null, 38.0), "m1");
        assertEquals(1, low.size());
        assertEquals("LOW_BATTERY", low.get(0).type());

        List<SarMissionEvent> geo = detector.detectSafety(
                telemetry(6, "d-02", DroneMode.SEARCH, null, null, true, 80.0), "m1");
        assertEquals(1, geo.size());
        assertEquals("GEOFENCE_VIOLATION", geo.get(0).type());

        List<SarMissionEvent> emergency = detector.detectSafety(
                telemetry(7, "d-03", DroneMode.EMERGENCY_LAND, null, null, null, 0.0), "m1");
        assertEquals(1, emergency.size());
        assertEquals("EMERGENCY_LAND", emergency.get(0).type());
    }

    @Test
    void kafkaSafetyMissionProducesMultipleViolations() throws Exception {
        Path missionPath = Path.of("../../missions/test-mission-kafka-safety.json").toAbsolutePath().normalize();
        Mission mission = MissionLoader.load(missionPath);
        SimulationEngine engine = new SimulationEngine(mission);
        SarSignificantEventDetector detector =
                new SarSignificantEventDetector().configure(mission.rulesOrDefault().rtbBatteryPct());
        Set<String> safetyTypes = new HashSet<>();
        SimulationResult result = engine.run(600, frame -> {
            for (SarMissionEvent event : detector.detect(frame, mission.missionId())) {
                if (SarSignificantEventDetector.isSafetyEvent(event.type())) {
                    safetyTypes.add(event.type());
                }
            }
        });
        assertTrue(safetyTypes.contains("LOW_BATTERY"), "expected LOW_BATTERY, got " + safetyTypes);
        assertTrue(safetyTypes.contains("GEOFENCE_VIOLATION"), "expected GEOFENCE_VIOLATION, got " + safetyTypes);
        assertTrue(
                safetyTypes.contains("EMERGENCY_LAND") || result.anyEmergencyLand(),
                "expected EMERGENCY_LAND, got " + safetyTypes);
        assertTrue(safetyTypes.size() >= 3, "expected at least three safety event types, got " + safetyTypes);
    }

    @Test
    void templateFallbackWhenOllamaDisabled() {
        SarNarratorService service = new SarNarratorService(new OllamaClient("http://127.0.0.1:1", "llama3:latest"), false);
        String text = service.narrateEvent(SarMissionEvent.of("TARGET_FOUND", 20, "m1", "t1", "d-02", "person"));
        assertTrue(text.contains("t1"));
        assertTrue(text.contains("d-02"));
    }

    @Test
    void safetyAnalystTemplatesForViolations() {
        SarSafetyAnalystService analyst =
                new SarSafetyAnalystService(new OllamaClient("http://127.0.0.1:1", "llama3:latest"), false);
        SarViolationAssessment geo = analyst.assessEvent(
                "airspace-v1",
                SarMissionEvent.of("GEOFENCE_VIOLATION", 10, "m1", null, "d-01", "no-fly"));
        assertEquals("HIGH", geo.severity());
        assertEquals("FREEZE_SECTOR", geo.recommendedAction());
    }

    private static DroneTelemetry telemetry(
            long tick,
            String droneId,
            DroneMode mode,
            Detection detection,
            String transferredFrom,
            Boolean geofenceViolation) {
        return new DroneTelemetry(
                droneId,
                tick,
                new Position(0, 0, 20),
                0,
                transferredFrom == null ? 80 : 38,
                mode,
                "s1",
                "t1",
                false,
                "primary",
                0,
                transferredFrom,
                detection,
                null,
                geofenceViolation);
    }

    private static DroneTelemetry telemetry(
            long tick,
            String droneId,
            DroneMode mode,
            Detection detection,
            String transferredFrom,
            Boolean geofenceViolation,
            double battery) {
        return new DroneTelemetry(
                droneId,
                tick,
                new Position(0, 0, 20),
                0,
                battery,
                mode,
                "s1",
                "t1",
                false,
                "primary",
                0,
                transferredFrom,
                detection,
                null,
                geofenceViolation);
    }
}

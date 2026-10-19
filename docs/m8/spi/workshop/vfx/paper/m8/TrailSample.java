package workshop.vfx.paper.m8;

/** M8 R1 SPI PROPOSAL (not production). One recorded trail position; samples of different segments are never joined. */
public record TrailSample(long tick, double x, double y, double z, long segment) { }

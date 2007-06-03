package com.ideanest.dscribe.opti;

import java.util.Random;

public class GeometricAnnealingStrategy implements AnnealingStrategy {

	private final double startTemperature, endTemperature, cycleFactor;
	private final int stepsPerCycle;
	private final Random random = new Random();
	private double temperature;
	private int stepsRemaining;

	public GeometricAnnealingStrategy(double startTemperature, double endTemperature, double cycleFactor, int stepsPerCycle) {
		this.startTemperature = startTemperature;
		this.endTemperature = endTemperature;
		this.cycleFactor = cycleFactor;
		this.stepsPerCycle = stepsPerCycle;
	}

	public void reset() {
		temperature = startTemperature;
		stepsRemaining = stepsPerCycle;
	}

	public boolean step() {
		if (--stepsRemaining < 0) {
			temperature *= cycleFactor;
			stepsRemaining = stepsPerCycle-1;
		}
		return temperature >= endTemperature;
	}

	public boolean accept(double costDelta) {
		return random.nextDouble() < Math.exp(-costDelta / temperature);
	}

}

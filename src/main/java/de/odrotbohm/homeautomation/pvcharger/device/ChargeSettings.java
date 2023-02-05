/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.odrotbohm.homeautomation.pvcharger.device;

import de.odrotbohm.homeautomation.pvcharger.Adjustment;
import de.odrotbohm.homeautomation.pvcharger.Power;
import de.odrotbohm.homeautomation.pvcharger.pv.PowerState;
import de.odrotbohm.homeautomation.pvcharger.pv.SlidingWindow;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Configuration settings to customize charging.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@With
@ConfigurationProperties("charging")
class ChargeSettings {

	static final ChargeSettings DEFAULTS = new ChargeSettings(5, 13, 5, 0, Duration.ofMinutes(10));

	private final @Getter(AccessLevel.PACKAGE) Power minCurrent, maxCurrent, acceptedExternalConsumption;
	private final int adjustmentWindow;
	private final Duration exceedChargeWindow;

	/**
	 * Creates a new {@link ChargeSettings} instance for the given minimal current, maximal current, adjustment window and
	 * accepted external consumption.
	 *
	 * @param minCurrent the minimum current that we need to charge with. Usually defined by the device to be charged.
	 * @param maxCurrent the maximum current we can charge with. Used as an upper bound for the values propagated to the
	 *          charge server.
	 * @param adjustmentWindow the number of measurements to consider when deciding whether to alter the charge state.
	 *          Used to prevent to erratic changes in the charge state if the power supply strongly oscillates. Larger
	 *          values usually mean smother charge changes but also greater lag in reaction to changes in power supply.
	 *          Can be countered by increasing the power data polling frequency (via
	 *          {@link de.odrotbohm.homeautomation.pvcharger.pv.PvProperties#getPollingFrequency()}.
	 * @param acceptedExternalConsumption the power we're willing to accept to feed from the grid. If the spare power
	 *          produced by the PV facility is close to the minimum amount of power needed for charge, we might decide to
	 *          accept some degree of grid consumption to be able to use the spare PV power. E.g. if we produce 800W of
	 *          spare power, but need 1200W minimum for charge, setting this to 400W will allow us to still charge but
	 *          accept that we need to pull the 400W form the grid.
	 */
	public ChargeSettings(int minCurrent, int maxCurrent, int adjustmentWindow, int acceptedExternalConsumption,
			Duration exceedChargeWindow) {

		this.minCurrent = Power.ofAmps(minCurrent == 0 ? 5 : minCurrent);
		this.maxCurrent = Power.ofAmps(maxCurrent == 0 ? 13 : maxCurrent);
		this.adjustmentWindow = adjustmentWindow == 0 ? 5 : adjustmentWindow;
		this.acceptedExternalConsumption = Power.ofWatt(acceptedExternalConsumption);
		this.exceedChargeWindow = exceedChargeWindow == null ? Duration.ofMinutes(10) : exceedChargeWindow;
	}

	private ChargeSettings(Power minCurrent, Power maxCurrent, Power acceptedExternalConsumption, int adjustmentWindow,
			Duration exceedChargeWindow) {

		this.minCurrent = minCurrent == null ? Power.ofAmps(5) : minCurrent;
		this.maxCurrent = maxCurrent == null ? Power.ofAmps(13) : maxCurrent;
		this.acceptedExternalConsumption = acceptedExternalConsumption == null ? Power.NONE : acceptedExternalConsumption;
		this.adjustmentWindow = adjustmentWindow < 0 ? 5 : adjustmentWindow;
		this.exceedChargeWindow = exceedChargeWindow == null ? Duration.ofMinutes(10) : exceedChargeWindow;
	}

	public boolean isCharging(Power power) {
		return power.isGreaterOrEqual(minCurrent);
	}

	public boolean impliesChargeStateChange(Adjustment adjustment) {
		return adjustment.passesThreshold(minCurrent);
	}

	/**
	 * Returns the current we have spare for charging. This is effectively a delta to the current setting considering the
	 * accepted external consumption as well.
	 *
	 * @param pv must not be {@literal null}.
	 * @return
	 */
	public Adjustment getAdjustment(Power current, PowerState pv) {

		var adjustment = Adjustment.withCurrent(current);
		var spareCurrent = pv.getSparePower();
		var candidate = adjustment.withAdjustment(spareCurrent.toAmpsRounded());

		// The adjustment would allow us to go beyond the minimum current: use adjustment
		if (!isBelowMinCurrent(candidate.getTarget())) {
			return candidate.withMax(maxCurrent);
		}

		// Can we get above the minimum current by using a little as possible external power

		var temp = Power.NONE;
		var delta = Power.ofAmps(1);

		while (acceptedExternalConsumption.isGreaterThan(temp) && isBelowMinCurrent(candidate.getTarget())) {
			temp = temp.plus(delta);
			candidate = candidate.andAdjustment(delta);
		}

		return isBelowMinCurrent(candidate.getTarget()) ? candidate.toZero() : candidate.withMax(maxCurrent);
	}

	/**
	 * Adjusts the given current to either zero if it's below the minimally required current or limits it to the maximum
	 * current.
	 *
	 * @param current
	 * @return
	 */
	public Power zeroOrWithinRange(Power current) {
		return isBelowMinCurrent(current) ? Power.NONE : current.min(maxCurrent);
	}

	public Adjustment withinRange(Adjustment adjustment) {
		return adjustment.withinRange(minCurrent, maxCurrent);
	}

	/**
	 * Returns the size of the sliding adjustment window. I.e. how many different {@link PowerState}s to consider to
	 * decide whether to in- or decrease charges.
	 *
	 * @return
	 */
	public int getAdjustmentWindow() {
		return adjustmentWindow;
	}

	/**
	 * Returns whether we should consider increasing the charge level based on the given {@link SlidingWindow}. This will
	 * consider the {@link SlidingWindow#getAverageExternalPower()} and compare this to the
	 * {@link #acceptedExternalConsumption} configured.
	 *
	 * @param states must not be {@literal null}.
	 * @return
	 */
	public boolean shouldIncreaseCharge(SlidingWindow states, Adjustment adjustment) {

		if (!adjustment.isFromZero()) {
			return true;
		}

		var averageExternalPower = states.getAverageExternalPower();

		if (!averageExternalPower.isGreaterThan(acceptedExternalConsumption)) {
			return true;
		}

		log.info("Not adjusting as average of {} over {} values exceeds accepable external power of {}.",
				averageExternalPower, states.size(), acceptedExternalConsumption);

		return false;
	}

	/**
	 * Returns whether we should consider decreasing the charge level based on the given {@link SlidingWindow}. This will
	 * consider the {@link SlidingWindow#getAverageExternalPower()} and compare this to the
	 * {@link #acceptedExternalConsumption} configured.
	 *
	 * @param states must not be {@literal null}.
	 * @return
	 */
	public boolean shouldDecreaseCharge(SlidingWindow states, Adjustment adjustment, @Nullable Instant triggerTime) {

		if (!adjustment.isToZero()) {
			return true;
		}

		Instant now = LocalDateTime.now().toInstant(ZoneOffset.UTC);
		Duration duration = triggerTime == null ? Duration.ofSeconds(0) : Duration.between(triggerTime, now);

		// Keep charging for at least the charge window
		if (duration.minus(exceedChargeWindow).isNegative()) {

			log.info("Not decreasing charge as we have not exceeded the minimum charge time ({} of {})",
					duration.toString(), exceedChargeWindow);
			return false;
		}

		var averageExternalPower = states.getAverageExternalPower();

		if (averageExternalPower.isGreaterThan(acceptedExternalConsumption)) {

			log.info("Adjusting due to {}.", states);

			return true;
		}

		if (!isBelowMinCurrent(adjustment.getTarget())) {
			return true;
		}

		log.info("Not adjusting as average of {} over {} values does not exceed accepable external power of {}.",
				averageExternalPower, states.size(), acceptedExternalConsumption);

		return false;
	}

	public boolean isBelowMinCurrent(Power current) {
		return minCurrent.isGreaterThan(current);
	}
}

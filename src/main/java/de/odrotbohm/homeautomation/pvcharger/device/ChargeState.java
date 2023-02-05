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
import de.odrotbohm.homeautomation.pvcharger.pv.PowerStatesFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * The primary state machine to capture the current charge state and transition it to new {@link ChargeState} by
 * consuming {@link PowerState}s. The fundamental state transition logic can be found in
 * {@link #apply(PowerState, ChargeSettings)}. {@link #transitionFor(PowerState, ChargeSettings, ChargingDevice)} will
 * propagate actual charge state transitions to the {@link ChargingDevice} handed into the method (start and stop
 * charging or adjust the charging current).
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@With(AccessLevel.PRIVATE)
class ChargeState {

	Power current;
	PowerStatesFactory states;
	ChargeSettings settings;
	@Nullable Instant triggerTime;

	static ChargeState init(ChargeSettings settings) {
		return forCurrent(Power.NONE, settings);
	}

	static ChargeState forCurrent(int current, ChargeSettings settings) {

		Power power = Power.ofAmps(current);

		return new ChargeState(power, PowerStatesFactory.instance(settings.getAdjustmentWindow()), settings, null);
	}

	static ChargeState forCurrent(Power power, ChargeSettings settings) {
		return new ChargeState(power, PowerStatesFactory.instance(settings.getAdjustmentWindow()), settings, null);
	}

	public boolean isCharging() {
		return settings.isCharging(current);
	}

	public ChargeState triggered() {
		return withTriggerTime(LocalDateTime.now().toInstant(ZoneOffset.UTC));
	}

	/**
	 * Transition the current {@link ChargeState} to a new one given the {@link PowerState} and apply the necessary
	 * actions on the given {@link ChargingDevice}.
	 *
	 * @param pv must not be {@literal null}.
	 * @param settings must not be {@literal null}.
	 * @param device must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ChargeState transitionFor(PowerState pv, ChargingDevice device) {

		Assert.notNull(pv, "PowerState must not be null!");
		Assert.notNull(settings, "ChargeSettings must not be null!");
		Assert.notNull(device, "ChargeClient must not be null!");

		if (!device.chargeRequired()) {

			log.info("Fully charged!");

			if (isCharging()) {
				device.stopCharging();
			}

			return withCurrent(Power.NONE);
		}

		var adjustment = settings.getAdjustment(current, pv);
		var newStates = states.add(pv);

		if (adjustment.isUnaltered()) {
			return withStates(newStates).withTriggerTime(null);
		}

		log.info("Candidate adjustment: {}.", adjustment);

		boolean alterCharge = !adjustment.isIncrease()
				? settings.shouldDecreaseCharge(states, adjustment, triggerTime)
				: settings.shouldIncreaseCharge(newStates, adjustment);
		boolean intendToSwitchOff = adjustment.isToZero();

		if (!alterCharge && intendToSwitchOff) {

			if (!adjustment.hasCurrent(settings.getMinCurrent())) {
				log.info("Skipping the adjustment but limiting to minimum charge.");
			}

			adjustment = settings.withinRange(adjustment);
			alterCharge = true;
		}

		// We attempt to switch off, set trigger time to continue loading for some time
		// Reset if we should charge again
		var newTriggerTime = intendToSwitchOff
				? triggerTime == null ? LocalDateTime.now().toInstant(ZoneOffset.UTC) : triggerTime
				: null;

		adjustment = alterCharge ? adjustment : Adjustment.none(current);

		var newAmps = settings.zeroOrWithinRange(adjustment.getTarget());
		var state = new ChargeState(newAmps, newStates, settings, newTriggerTime);

		try {

			var isCurrentlyCharging = isCharging();
			var isSupposedToCharge = state.isCharging();

			if (isCurrentlyCharging && !isSupposedToCharge) {

				device.stopCharging();

			} else if (!isCurrentlyCharging && isSupposedToCharge) {

				device.startCharging(state.getCurrent());

			} else if (!adjustment.isUnaltered()) {
				device.adjustChargePower(state.getCurrent());
			}

			return state;

		} catch (Exception o_O) {

			log.warn(o_O.getMessage());

			return this;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ChargeState(current=%sA)".formatted(current.inAmps());
	}
}

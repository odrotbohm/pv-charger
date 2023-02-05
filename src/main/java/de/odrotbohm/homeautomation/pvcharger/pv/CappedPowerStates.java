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
package de.odrotbohm.homeautomation.pvcharger.pv;

import de.odrotbohm.homeautomation.pvcharger.Power;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class CappedPowerStates implements PowerStates {

	private final List<PowerState> states;
	private final int capacity;

	/**
	 * Creates a new {@link CappedPowerStates} initialized with the given {@link PowerState} and capacity.
	 *
	 * @param state must not be {@literal null}.
	 * @param capacity must be greater than zero.
	 * @return will never be {@literal null}.
	 */
	public static CappedPowerStates init(PowerState state, int capacity) {

		Assert.isTrue(capacity > 0, "Capacity must be greater than zero!");

		List<PowerState> states = new ArrayList<>();

		while (states.size() < capacity - 1) {
			states.add(PowerState.NONE);
		}

		states.add(state);

		return new CappedPowerStates(states, capacity);
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.car.ChargeState.PowerStates#add(de.odrotbohm.homeautomation.pvcharger.pv.PowerState)
	 */
	public CappedPowerStates add(PowerState state) {

		Assert.notNull(state, "PowerState must not be null!");

		var newStates = new ArrayList<>(states.size() == capacity ? states.subList(1, capacity) : states);
		newStates.add(state);

		return new CappedPowerStates(newStates, capacity);
	}

	/**
	 * Returns the most recent {@link PowerState}.
	 *
	 * @return
	 */
	public PowerState getMostRecent() {
		return states.get(states.size() - 1);
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.car.ChargeState.PowerStates#getAverageExternalPower()
	 */
	@Override
	public Power getAverageExternalPower() {

		var size = states.size();

		if (size == 0) {
			return Power.NONE;
		}

		return states.stream()
				.map(PowerState::getSparePower)
				.map(Power::negate)
				.reduce(Power.NONE, Power::plus)
				.divideBy(states.size());
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.pv.SlidingWindow#size()
	 */
	@Override
	public int size() {
		return states.size();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "CappedPowerStates avg/med: %s, %s".formatted(getAverageExternalPower().inWatt(),
				states.stream().map(PowerState::toString).collect(Collectors.joining(",", "[", "]")));
	}
}

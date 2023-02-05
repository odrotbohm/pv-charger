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

/**
 * A sliding window of {@link PowerState} instances that we can calculate statistics on. Primarily a factory to create
 * {@link CappedPowerStates} and initialize those with a capacity.
 *
 * @author Oliver Drotbohm
 */
public interface PowerStatesFactory extends SlidingWindow {

	/**
	 * Creates a new {@link PowerStatesFactory} for the
	 *
	 * @param capacity must be greater than zero.
	 * @return
	 */
	public static PowerStatesFactory instance(int capacity) {
		return state -> CappedPowerStates.init(state, capacity);
	}

	/**
	 * Returns a new {@link PowerStates} adding the given {@link PowerState} to the current one.
	 *
	 * @param state must not be {@literal null}.
	 * @return
	 */
	PowerStates add(PowerState state);

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.pv.SlidingWindow#getAverageExternalPower()
	 */
	default Power getAverageExternalPower() {
		return Power.NONE;
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.pv.SlidingWindow#size()
	 */
	@Override
	default int size() {
		return 0;
	}
}

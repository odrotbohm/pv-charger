/*
 * Copyright 2022 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import de.odrotbohm.homeautomation.pvcharger.Power;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * @author Oliver Drotbohm
 */
public class CappedPowerStatesUnitTest {

	@Test
	void calculatedAveragePowerStatesViaAverageAndMedian() {

		var acceptedDelta = Offset.offset(0.1);
		var solarBase = PowerState.forSolarPower(Power.ofWatt(3000));
		var state = solarBase.consuming(Power.ofWatt(500));

		var states = CappedPowerStates.init(state, 3);

		// Average of 3 [0, 0, 500]
		assertThat(states.getAverageExternalPower().inWatt()).isCloseTo(166.6, acceptedDelta);

		// Average of 3 [0, 500, 100]
		states = states.add(solarBase.consuming(Power.ofWatt(100)));
		assertThat(states.getAverageExternalPower().inWatt()).isCloseTo(200.0, acceptedDelta);

		// Median of 3 as soon as we have the window filled. [500, 100, 5000]
		states = states.add(solarBase.consuming(Power.ofWatt(5000)));
		assertThat(states.getAverageExternalPower().inWatt()).isCloseTo(500.0, acceptedDelta);

		// New entry pushing old ones out [100, 5000, 2000]
		states = states.add(solarBase.consuming(Power.ofWatt(2000)));
		assertThat(states.getAverageExternalPower().inWatt()).isCloseTo(2000.0, acceptedDelta);
	}
}

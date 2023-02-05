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

import org.junit.jupiter.api.Test;

/**
 * @author Oliver Drotbohm
 */
public class PowerStateUnitTests {

	@Test
	void testName() {

		var builder = PowerState.forSolarPower(Power.ofWatt(1500));

		assertThat(builder.consuming(Power.ofWatt(200)).predict(Power.ofWatt(400)))
				.isEqualTo(builder.consuming(Power.ofWatt(600)));
		assertThat(builder.feedingIn(Power.ofWatt(200)).predict(Power.ofWatt(400)))
				.isEqualTo(builder.consuming(Power.ofWatt(200)));
	}
}

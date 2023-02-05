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
package de.odrotbohm.homeautomation.pvcharger.device;

import static org.assertj.core.api.Assertions.*;

import de.odrotbohm.homeautomation.pvcharger.Power;
import de.odrotbohm.homeautomation.pvcharger.pv.PowerState;

import org.junit.jupiter.api.Test;

/**
 * @author Oliver Drotbohm
 */
public class ChargeSettingsUnitTests {

	@Test
	void testName() {

		var pv = PowerState.forSolarPower(Power.ofWatt(2500))
				.consuming(Power.ofWatt(1800));

		var settings = ChargeSettings.DEFAULTS
				.withAcceptedExternalConsumption(Power.ofWatt(1000));

		var adjustment = settings.getAdjustment(Power.ofAmps(7), pv);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(-7));
		assertThat(adjustment.isToZero()).isTrue();

		adjustment = settings.getAdjustment(Power.ofAmps(8), pv);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(-8));
		assertThat(adjustment.isToZero()).isTrue();

		adjustment = settings.getAdjustment(Power.ofAmps(9), pv);

		assertThat(adjustment.getDelta().inRoundedAmps()).isEqualTo(-4);
		assertThat(adjustment.isToZero()).isFalse();
	}

	@Test
	void adjustsToMinimalChargeLevel() {

		var powerState = PowerState.forSolarPower(Power.ofWatt(1200)).feedingIn(Power.ofWatt(1200));
		var adjustment = ChargeSettings.DEFAULTS.getAdjustment(Power.NONE, powerState);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(5));
	}

	@Test
	void adjustsToMinimalChargeLevel2() {

		var powerState = PowerState.forSolarPower(Power.ofWatt(2000))
				.feedingIn(Power.ofWatt(1200));

		var adjustment = ChargeSettings.DEFAULTS.getAdjustment(Power.NONE, powerState);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(5));
	}

	@Test
	void decreasesToMinimalChargeLevelToAvoidConsumption() {

		var powerState = PowerState.forSolarPower(Power.ofWatt(2500))
				.consuming(Power.ofWatt(500));

		var adjustment = ChargeSettings.DEFAULTS.getAdjustment(Power.ofAmps(8), powerState);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(-3));
	}

	@Test
	void decreasesToMinimalChargeLevelToAvoidConsumptionAndReachAcceptableConsumption() {

		var powerState = PowerState.forSolarPower(Power.ofWatt(2500))
				.consuming(Power.ofWatt(1500));

		var adjustment = ChargeSettings.DEFAULTS
				.withAcceptedExternalConsumption(Power.ofWatt(1000))
				.getAdjustment(Power.ofAmps(8), powerState);

		assertThat(adjustment.getDelta()).isEqualTo(Power.ofAmps(-3));
	}
}

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.odrotbohm.homeautomation.pvcharger.Power;
import de.odrotbohm.homeautomation.pvcharger.pv.PowerState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for state transitions in {@link ChargeState}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
public class ChargeStateUnitTests {

	@Mock ChargingDevice device;

	ChargeSettings settings = ChargeSettings.DEFAULTS
			.withMinCurrent(Power.ofAmps(3))
			.withMaxCurrent(Power.ofAmps(13))
			.withAdjustmentWindow(3)
			.withAcceptedExternalConsumption(Power.ofWatt(0));

	@BeforeEach
	void setUp() {
		when(device.chargeRequired()).thenReturn(true);
	}

	@Test
	void doesNotStartCharginIfAvailablePowerDoesNotExceedThreshold() {

		var state = ChargeState.init(settings);

		var result = state.transitionFor(PowerState.forSolarPowerOnly(Power.ofWatt(200)), device);

		assertThat(result.isCharging()).isFalse();
		verifyNoInteractions(device);
	}

	@Test
	void startsChargingIfPowerExceedsThreshold() {

		var state = ChargeState.init(settings);

		var powerState = PowerState.forSolarPower(Power.ofWatt(1000))
				.feedingIn(Power.ofWatt(700));

		var result = state.transitionFor(powerState, device);

		// Expect 3 ampere as we have 700 Watts left (300 base consumption)

		assertThat(result.isCharging()).isTrue();
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(3));

		verify(device).startCharging(Power.ofAmps(3));
	}

	@Test
	void adjustsPowerUp() {

		var state = ChargeState.forCurrent(4, settings);
		var powerState = PowerState.forSolarPower(Power.ofWatt(1500))
				.feedingIn(Power.ofWatt(300));
		var result = state.transitionFor(powerState, device);

		assertThat(result.isCharging()).isTrue();
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(5));

		verify(device).adjustChargePower(Power.ofAmps(5));
	}

	@Test
	void adjustsPowerDown() {

		var powerState = PowerState
				.forSolarPower(Power.ofWatt(1000))
				.consuming(Power.ofWatt(600));

		var state = ChargeState.forCurrent(6, settings);

		var result = state.transitionFor(powerState, device);

		assertThat(result.isCharging()).isTrue();
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(3));

		verify(device).adjustChargePower(Power.ofAmps(3));
	}

	@Test
	void adjustsPowerDownForExternalUse() {

		var powerState = PowerState
				.forSolarPower(Power.ofWatt(1000))
				.consuming(Power.ofWatt(500));

		var state = ChargeState.forCurrent(6, settings);

		var result = state.transitionFor(powerState, device);

		assertThat(result.isCharging()).isTrue();
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(3));

		verify(device).adjustChargePower(Power.ofAmps(3));
	}

	@Test
	void adjustsPowerDownToStopCharge() {

		var powerState = PowerState
				.forSolarPower(Power.ofWatt(1000))
				.consuming(Power.ofWatt(1000));

		var state = ChargeState.forCurrent(6, settings);

		var result = state.transitionFor(powerState, device);

		assertThat(result.isCharging()).isFalse();
		assertThat(result.getCurrent()).isEqualTo(Power.NONE);

		verify(device).stopCharging();
	}

	@Test
	void increasesChargeLevelAfter() {

		var solarBase = PowerState.forSolarPower(Power.ofWatt(2500));
		var state = ChargeState.forCurrent(6, settings.withAdjustmentWindow(2));
		var result = state.transitionFor(solarBase.consuming(Power.ofWatt(300)), device);

		// Reacts to external consumption overflow
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(4));

		var altered = solarBase.feedingIn(Power.ofWatt(300));

		result = result.transitionFor(altered, device);

		// Does not react to available current until sliding window exceeds
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(4));

		result = result.transitionFor(altered, device);

		// Sliding window exceeds: increase charge
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(5));

		result = result.transitionFor(solarBase.consuming(Power.ofWatt(150)), device);
		assertThat(result.getCurrent()).isEqualTo(Power.ofAmps(4));
	}

	@Test
	void stopsChargingIfDeviceIsFullyCharged() {

		var state = ChargeState.init(settings);
		var power = PowerState
				.forSolarPower(Power.ofWatt(2500))
				.feedingIn(Power.ofWatt(2000));

		state = state.transitionFor(power, device);

		// Is charging with available power
		assertThat(state.isCharging()).isTrue();
		assertThat(state.getCurrent()).isEqualTo(Power.ofAmps(9));

		// Device fully charged
		when(device.chargeRequired()).thenReturn(false);

		state = state.transitionFor(power, device);

		// Has stopped charging
		assertThat(state.isCharging()).isFalse();
		verify(device).stopCharging();
	}
}

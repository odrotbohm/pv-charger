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
package de.odrotbohm.homeautomation.pvcharger.device.tesla;

import de.odrotbohm.homeautomation.pvcharger.Power;
import de.odrotbohm.homeautomation.pvcharger.device.ChargingDevice;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * {@link ChargingDevice} implementation that interacts with the Tesla API.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@Component
@RequiredArgsConstructor
class Tesla implements ChargingDevice {

	private static final String SERVER_URL = "https://owner-api.teslamotors.com/api/1";
	private static final String INFO_URL = SERVER_URL + "/vehicles";

	private static final String VEHICLE_TEMPLATE = INFO_URL + "/{id}";
	private static final String WAKE_UP_TEMPLATE = INFO_URL + "/{id}/wake_up";
	private static final String CHARGE_INFO_TEMPLATE = INFO_URL + "/{id}/data_request/charge_state";
	private static final String CHARGE_STOP_TEMPLATE = INFO_URL + "/{id}/command/charge_stop";
	private static final String CHARGE_START_TEMPLATE = INFO_URL + "/{id}/command/charge_start";
	private static final String CHARGING_AMPS_TEMPLATE = INFO_URL + "/{id}/command/set_charging_amps";

	private static final JsonPath CHARGE_STATE = JsonPath.compile("$.response.charging_state");
	private static final JsonPath CHARGE_CURRENT_PATH = JsonPath.compile("$.response.charger_actual_current");
	private static final JsonPath VEHICLE_STATE_PATH = JsonPath.compile("$.response.state");

	private static final JsonPath BATTERY_LEVEL = JsonPath.compile("$.response.battery_level");
	private static final JsonPath CHARGE_LIMIT = JsonPath.compile("$.response.charge_limit_soc");

	private final TeslaAuthentication authentication;
	private final TeslaProperties settings;

	private final ExpiringMap<String, DocumentContext> chargeStates = ExpiringMap.builder()
			.expiration(1, TimeUnit.MINUTES)
			.build();

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.car.RemoteChargeState#heartbeat()
	 */
	@Override
	public void heartbeat() {

		authentication.withAuthentication(operations -> operations.headForHeaders(INFO_URL));

		log.info("Successfully connected to {}.", SERVER_URL);
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.device.ChargingDevice#chargeRequired()
	 */
	@Override
	public boolean chargeRequired() {

		var context = chargeStates.computeIfAbsent(settings.getCarId(), id -> {

			return withWokenUp(() -> {

				var response = authentication.withAuthentication(
						ops -> ops.getForEntity(CHARGE_INFO_TEMPLATE, String.class, settings.getCarId()));

				var document = JsonPath.parse(response.getBody());

				var batteryLevel = document.read(BATTERY_LEVEL, int.class);
				var chargeLimit = document.read(CHARGE_LIMIT, int.class);

				log.debug("Charge limit set to {}%. Current charge at {}%.", batteryLevel, chargeLimit);

				return document;
			});
		});

		var batteryLevel = context.read(BATTERY_LEVEL, int.class);
		var chargeLimit = context.read(CHARGE_LIMIT, int.class);

		return batteryLevel < chargeLimit;
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.device.ChargingDevice#getChargePower()
	 */
	@Override
	public Power getChargePower() {

		return withWokenUp(() -> {

			try {

				var response = authentication.withAuthentication(
						ops -> ops.getForEntity(CHARGE_INFO_TEMPLATE, String.class, settings.getCarId()));
				var context = JsonPath.parse(response.getBody());
				var state = context.read(CHARGE_STATE, String.class);

				return state != null && state.equals("Stopped")
						? Power.NONE
						: Power.ofAmps(context.read(CHARGE_CURRENT_PATH, int.class));

			} catch (HttpStatusCodeException o_O) {

				log.warn(o_O.getMessage());

				return Power.NONE;
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.car.RemoteChargeState#stopCharging()
	 */
	@Override
	public void stopCharging() {

		log.info("Stop charging…");

		var request = RequestEntity
				.post(new UriTemplate(CHARGE_STOP_TEMPLATE).expand(settings.getCarId()))
				.contentType(MediaType.APPLICATION_JSON)
				.build();

		authentication.withAuthentication(ops -> ops.exchange(request, Void.class));

		log.info("Stopped charging.");
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.device.ChargingDevice#startCharging(de.odrotbohm.homeautomation.pvcharger.Power)
	 */
	@Override
	public void startCharging(Power current) {

		withWokenUp(() -> {

			log.info("Start charging…");

			var request = RequestEntity
					.post(new UriTemplate(CHARGE_START_TEMPLATE).expand(settings.getCarId()))
					.contentType(MediaType.APPLICATION_JSON)
					.build();

			authentication.withAuthentication(ops -> ops.exchange(request, Void.class));

			log.info("Charging started.");

			adjustChargePower(current);

			return null;
		});
	}

	/*
	 * (non-Javadoc)
	 * @see de.odrotbohm.homeautomation.pvcharger.device.ChargingDevice#adjustChargePower(de.odrotbohm.homeautomation.pvcharger.Power)
	 */
	@Override
	public void adjustChargePower(Power current) {

		log.info("Adjusting charge to {}A…", current.inRoundedAmps());

		var request = RequestEntity
				.post(new UriTemplate(CHARGING_AMPS_TEMPLATE).expand(settings.getCarId()))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body("{ \"charging_amps\" : %s }".formatted(current.inRoundedAmps()));

		authentication.withAuthentication(ops -> ops.exchange(request, String.class));

		log.info("Charge adjusted.", current);
	}

	/**
	 * Wakes up the vehicle.
	 */
	@SneakyThrows
	private void wakeUp() {

		var uri = new UriTemplate(WAKE_UP_TEMPLATE).expand(settings.getCarId());

		var request = RequestEntity
				.post(uri)
				.contentType(MediaType.APPLICATION_JSON)
				.build();

		log.info("Trigger wake up…");

		try {

			authentication.withAuthentication(operations -> operations.exchange(request, Void.class));

			log.info("Successfully woken up vehicle!");
			return;

		} catch (HttpStatusCodeException o_O) {

			if (o_O.getStatusCode() != HttpStatus.REQUEST_TIMEOUT) {
				throw o_O;
			}
		}

		for (int i = 0; i < 5; i++) {

			log.info("Not alive yet, continue waiting…");

			Thread.sleep(5000);

			var state = vehicleInfo();

			if (state == VehicleState.ONLINE) {
				log.info("Successfully woken up vehicle!");
				return;
			}
		}

		log.info("Failed to wake up vehicle!");
	}

	/**
	 * Executes the given {@link Runnable}, making sure that the vehicle is woken up.
	 *
	 * @param runnable must not be {@literal null}.
	 */
	private <T> T withWokenUp(Supplier<T> runnable) {

		Assert.notNull(runnable, "The given Runnable must not be null!");

		var state = vehicleInfo();

		switch (state) {
			case ASLEEP:
				wakeUp();
			case UNDEFINED:
			case ONLINE:
				return runnable.get();
			default:
				throw new IllegalStateException("Invalid vehicle state %s!".formatted(state));
		}
	}

	private VehicleState vehicleInfo() {

		var uri = new UriTemplate(VEHICLE_TEMPLATE).expand(settings.getCarId());
		var request = new RequestEntity<>(HttpMethod.GET, uri);

		var response = authentication.withAuthentication(operations -> operations.exchange(request, String.class));
		var context = JsonPath.parse(response.getBody());
		var result = context.read(VEHICLE_STATE_PATH, String.class);

		return switch (result) {
			case "asleep" -> VehicleState.ASLEEP;
			case "online" -> VehicleState.ONLINE;
			default -> VehicleState.UNDEFINED;
		};
	}

	enum VehicleState {
		ONLINE, ASLEEP, UNDEFINED;
	}
}

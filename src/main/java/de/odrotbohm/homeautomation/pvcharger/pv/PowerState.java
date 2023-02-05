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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.jmolecules.event.types.DomainEvent;

/**
 * A value capturing the power state of the PV facility.
 *
 * @author Oliver Drotbohm
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PowerState implements DomainEvent {

	public static PowerState NONE = new Neutral(Power.NONE);

	protected final @Getter Power solarPower;

	public static PowerStateBuilder forSolarPower(Power power) {
		return new PowerStateBuilder(power);
	}

	public static PowerState forSolarPowerOnly(Power power) {
		return new Neutral(power);
	}

	public boolean usesExternalPower() {
		return getExternalPower().isGreaterThanZero();
	}

	public Power getExternalPower() {
		return Power.NONE;
	}

	public abstract Power getSparePower();

	public abstract PowerState predict(Power power);

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "%.1f".formatted(getSparePower().inWatt());
	}

	@RequiredArgsConstructor
	public static class PowerStateBuilder {

		private final Power solar;

		public PowerState consuming(Power external) {
			return new ConsumingExternal(solar, external);
		}

		public PowerState feedingIn(Power feedIn) {
			return new FeedingIn(solar, feedIn);
		}
	}

	private static class Neutral extends PowerState {

		private Neutral(Power solar) {
			super(solar);
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#getSparePower()
		 */
		@Override
		public Power getSparePower() {
			return Power.NONE;
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#predict(de.odrotbohm.homeautomation.pvcharger.Power)
		 */
		@Override
		public PowerState predict(Power power) {

			if (power.isZero()) {
				return this;
			}

			return power.isGreaterThanZero()
					? new ConsumingExternal(solarPower, power)
					: new FeedingIn(solarPower, power);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "Neutral(solar = %.1fW)".formatted(getSolarPower().inWatt());
		}

	}

	@EqualsAndHashCode(callSuper = true)
	private static class ConsumingExternal extends PowerState {

		Power external;

		private ConsumingExternal(Power solar, Power external) {
			super(solar);
			this.external = external;
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#getExternalPower()
		 */
		@Override
		public Power getExternalPower() {
			return external;
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#getSparePower()
		 */
		@Override
		public Power getSparePower() {
			return external.negate();
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#predict(de.odrotbohm.homeautomation.pvcharger.Power)
		 */
		@Override
		public PowerState predict(Power power) {

			Power result = external.plus(power);

			return result.isGreaterThanZero()
					? new ConsumingExternal(solarPower, result)
					: new FeedingIn(solarPower, result);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "Consuming(solar = %.1fW, external = %.1fW)"
					.formatted(getSolarPower().inWatt(), getExternalPower().inWatt());
		}
	}

	@EqualsAndHashCode(callSuper = true)
	private static class FeedingIn extends PowerState {

		Power feedIn;

		private FeedingIn(Power solar, Power feedIn) {

			super(solar);
			this.feedIn = feedIn;
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#getSparePower()
		 */
		@Override
		public Power getSparePower() {
			return feedIn;
		}

		/*
		 * (non-Javadoc)
		 * @see de.odrotbohm.homeautomation.pvcharger.pv.PowerState#predict(de.odrotbohm.homeautomation.pvcharger.Power)
		 */
		@Override
		public PowerState predict(Power power) {

			var delta = feedIn.minus(power);

			return delta.isGreaterThanZero() ? new FeedingIn(solarPower, delta)
					: new ConsumingExternal(solarPower, delta.negate());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "FeedingIn(solar = %.1fW, feeding = %.1fW)"
					.formatted(getSolarPower().inWatt(), getSparePower().inWatt());
		}
	}
}

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
package de.odrotbohm.homeautomation.pvcharger;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import org.springframework.lang.Nullable;

/**
 * @author Oliver Drotbohm
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Power implements Comparable<Power> {

	private static final int VOLTAGE = 220;

	public static final Power NONE = new Power(0);

	private final double current;

	public static Power ofAmps(@Nullable Integer amps) {
		return amps == null ? NONE : new Power(amps);
	}

	public static Power ofWatt(@Nullable Integer watt) {
		return watt == null ? NONE : ofWatt(watt.doubleValue());
	}

	public static Power ofWatt(double watt) {
		return new Power(watt / VOLTAGE);
	}

	public double inWatt() {
		return current * VOLTAGE;
	}

	public double inAmps() {
		return current;
	}

	public Power toAmpsRounded() {
		return Power.ofAmps(inRoundedAmps());
	}

	public int inRoundedAmps() {
		return (int) Math.floor(current);

		// return (int) (current > 0 ? Math.floor(current) : Math.ceil(current));
	}

	public Power negate() {
		return new Power(-current);
	}

	public boolean isGreaterThan(Power that) {
		return this.current > that.current;
	}

	public boolean isGreaterOrEqual(Power that) {
		return this.current >= that.current;
	}

	public boolean isGreaterThanZero() {
		return current > 0;
	}

	public boolean isZero() {
		return current == 0.0;
	}

	public Power min(Power that) {
		return isGreaterThan(that) ? that : this;
	}

	public Power plus(Power power) {
		return new Power(this.current + power.current);
	}

	public Power minus(Power power) {
		return new Power(this.current - power.current);
	}

	/**
	 * @param i
	 * @return
	 */
	public Power divideBy(int i) {
		return new Power(current / i);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Power that) {

		var diff = this.current - that.current;

		return diff == 0.0 ? 0 : diff > 0 ? 1 : -1;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "%sW".formatted(inWatt());
	}

}

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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Oliver Drotbohm
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Adjustment {

	protected final Power current;
	protected final @Getter Power delta;

	public static Adjustment none(Power current) {
		return new Adjustment(current, Power.NONE);
	}

	public static AdjustmentBuilder withCurrent(Power current) {
		return new AdjustmentBuilder(current, Power.NONE);
	}

	public Adjustment withMax(Power power) {

		return getTarget().isGreaterThan(power)
				? new Adjustment(current, power.minus(current))
				: this;
	}

	public boolean hasCurrent(Power power) {
		return current.equals(power);
	}

	public Power getTarget() {
		return current.plus(delta);
	}

	public boolean isUnaltered() {
		return delta.isZero();
	}

	public boolean isIncrease() {
		return delta.isGreaterThanZero();
	}

	public boolean isToZero() {
		return current.equals(delta.negate());
	}

	public boolean isFromZero() {
		return current.equals(Power.NONE);
	}

	public boolean passesThreshold(Power threshold) {
		return current.isGreaterThan(threshold) != getTarget().isGreaterThan(threshold);
	}

	public Adjustment withinRange(Power min, Power max) {

		var target = getTarget();

		if (min.isGreaterThan(target)) {
			return toTarget(min);
		}

		if (target.isGreaterThan(max)) {
			return toTarget(max);
		}

		return this;
	}

	private Adjustment toTarget(Power power) {
		return withCurrent(current).withAdjustment(power.minus(current));
	}

	@Override
	public String toString() {

		String sign = !delta.isGreaterThanZero() ? "" : "+";

		return "%sA --(%s%sA)--> %sA".formatted(current.inAmps(), sign, delta.inAmps(), getTarget().inAmps());
	}

	public static class AdjustmentBuilder extends Adjustment {

		private AdjustmentBuilder(Power current, Power adjustment) {
			super(current, adjustment);
		}

		public AdjustmentBuilder andAdjustment(Power power) {
			return new AdjustmentBuilder(current, delta.plus(power));
		}

		public AdjustmentBuilder withAdjustment(Power power) {
			return new AdjustmentBuilder(current, power);
		}

		public Adjustment toRoundedAmps() {
			return new Adjustment(current, delta.toAmpsRounded());
		}

		public Adjustment toZero() {
			return withAdjustment(current.negate());
		}
	}
}

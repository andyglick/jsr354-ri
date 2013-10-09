/*
 * Copyright (c) 2012, 2013, Credit Suisse (Anatole Tresch), Werner Keil.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.javamoney.moneta.function;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;

import javax.money.MonetaryAdjuster;
import javax.money.MonetaryAmount;

import org.javamoney.moneta.Money;

/**
 * This class allows to extract the permil of a {@link MonetaryAmount} instance.
 * 
 * @version 0.5
 * @author Anatole Tresch
 * @author Werner Keil
 * 
 * @see <a href="http://en.wikipedia.org/wiki/Per_mil">Wikipedia: Per mil</a>
 */
final class Permil<T extends MonetaryAmount> implements MonetaryAdjuster {

	private static final MathContext DEFAULT_MATH_CONTEXT = initDefaultMathContext();

	private static final BigDecimal ONE_THOUSAND = new BigDecimal(1000,
			MathContext.DECIMAL64);

	private final BigDecimal permilValue;

	/**
	 * Get {@link MathContext} for {@link Permil} instances.
	 * 
	 * @return the {@link MathContext} to be used, by default
	 *         {@link MathContext#DECIMAL64}.
	 */
	private static MathContext initDefaultMathContext() {
		// TODO Initialize default, e.g. by system properties, or better:
		// classpath properties!
		return MathContext.DECIMAL64;
	}

	/**
	 * Access the shared instance of {@link Permil} for use.
	 * 
	 * @return the shared instance, never {@code null}.
	 */
	Permil(final BigDecimal decimal) {
		permilValue = calcPermil(decimal);
	}

	/**
	 * Gets the permil of the amount.
	 * <p>
	 * This returns the monetary amount in permil. For example, for 10% 'EUR
	 * 2.35' will return 0.235.
	 * <p>
	 * This is returned as a {@code MonetaryAmount}.
	 * 
	 * @return the permil result of the amount, never {@code null}
	 */
	@Override
	public MonetaryAmount adjustInto(MonetaryAmount amount) {
		return Money.from(amount).multiply(
				permilValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return NumberFormat.getInstance().format(
				permilValue.multiply(ONE_THOUSAND, DEFAULT_MATH_CONTEXT)) +
				" \u2030";
	}

	/**
	 * Calculate a BigDecimal value for a Permil e.g. "3" (3 permil) will
	 * generate .003
	 * 
	 * @return java.math.BigDecimal
	 * @param decimal
	 *            java.math.BigDecimal
	 */
	private static final BigDecimal calcPermil(BigDecimal decimal) {
		return decimal.divide(ONE_THOUSAND, DEFAULT_MATH_CONTEXT); // we now
																	// have .003
	}

//	@Override
//	public String getDisplayName(Locale locale) {
//		// TODO i18n?
//		return toString();
//	}
}